package dev.aftermath.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.aftermath.AftermathMod;
import dev.aftermath.character.AftermathAttachments;
import dev.aftermath.character.CharacterCatalog;
import dev.aftermath.character.CharacterProfile;
import dev.aftermath.character.CharacterProfession;
import dev.aftermath.character.CharacterTrait;
import dev.aftermath.config.AftermathServerConfig;
import dev.aftermath.network.payload.CharacterCatalogPayload;
import dev.aftermath.network.payload.CharacterSelectionRequestPayload;
import dev.aftermath.network.payload.CharacterSelectionResultPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** Register payload types and handle untrusted client profile requests. */
public final class CharacterNetwork {
    private static final Gson GSON = new GsonBuilder().create();
    private static final int MAX_CATALOG_CHARS = 24_000;
    private static final int MAX_REQUEST_CHARS = 2_048;
    private static final int MAX_TRAITS_PER_REQUEST = 32;

    private CharacterNetwork() {}

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(CharacterCatalogPayload.TYPE, CharacterCatalogPayload.STREAM_CODEC);
        registrar.playToClient(CharacterSelectionResultPayload.TYPE, CharacterSelectionResultPayload.STREAM_CODEC);
        registrar.playToServer(
                CharacterSelectionRequestPayload.TYPE,
                CharacterSelectionRequestPayload.STREAM_CODEC,
                CharacterNetwork::handleSelectionRequest
        );
    }

    public static boolean sendCatalog(ServerPlayer player, boolean openScreen) {
        CharacterCatalog.Snapshot snapshot = CharacterCatalog.snapshot();
        if (!snapshot.isLoaded()) {
            return false;
        }

        List<ProfessionView> professions = snapshot.professions().values().stream()
                .map(profession -> new ProfessionView(
                        profession.id().toString(),
                        profession.nameKey(),
                        profession.descriptionKey(),
                        profession.startingPoints(),
                        profession.allowedTraits().stream().map(Object::toString).sorted().toList()
                ))
                .toList();

        List<TraitView> traits = snapshot.traits().values().stream()
                .map(trait -> new TraitView(
                        trait.id().toString(),
                        trait.nameKey(),
                        trait.descriptionKey(),
                        trait.category() == CharacterTrait.Category.POSITIVE ? "positive" : "negative",
                        trait.pointCost(),
                        trait.pointCredit(),
                        trait.conflicts().stream().map(Object::toString).sorted().toList()
                ))
                .toList();

        String json = GSON.toJson(new CatalogView(professions, traits));
        if (json.length() > MAX_CATALOG_CHARS) {
            AftermathMod.LOGGER.error("Character catalog payload is too large ({} characters); refusing to send it to {}.",
                    json.length(), player.getGameProfile().getName());
            return false;
        }

        PacketDistributor.sendToPlayer(player, new CharacterCatalogPayload(json, openScreen));
        return true;
    }

    private static void handleSelectionRequest(CharacterSelectionRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }

        if (!AftermathServerConfig.ENABLE_CHARACTER_CREATION.get()) {
            sendFailure(player, "feature_disabled", "", "", 0, 0);
            return;
        }

        String professionInput = payload.professionId();
        String rawTraits = payload.traitIdsCsv();
        if (professionInput == null || professionInput.length() > 128
                || rawTraits == null || rawTraits.length() > MAX_REQUEST_CHARS) {
            sendFailure(player, "invalid_request", "", "", 0, 0);
            return;
        }

        CharacterProfile currentProfile = player.getData(AftermathAttachments.CHARACTER_PROFILE);
        if (currentProfile.complete()) {
            sendFailure(player, "reselection_blocked", "", "", 0, 0);
            return;
        }

        if (!CharacterCatalog.snapshot().isLoaded()) {
            sendFailure(player, "definitions_not_loaded", "", "", 0, 0);
            return;
        }

        List<String> selectedTraits = new ArrayList<>();
        if (!rawTraits.isBlank()) {
            for (String token : rawTraits.split("[,\\s]+")) {
                if (!token.isBlank()) {
                    selectedTraits.add(token);
                    if (selectedTraits.size() > MAX_TRAITS_PER_REQUEST) {
                        sendFailure(player, "too_many_traits", "", "", 0, 0);
                        return;
                    }
                }
            }
        }

        CharacterCatalog.SelectionResult result =
                CharacterCatalog.validateSelection(professionInput, selectedTraits);
        if (!result.accepted()) {
            sendFailure(player, result.errorCode(), result.argumentOne(), result.argumentTwo(),
                    result.requiredPoints(), result.availablePoints());
            return;
        }

        player.setData(AftermathAttachments.CHARACTER_PROFILE, result.profile());
        PacketDistributor.sendToPlayer(player, new CharacterSelectionResultPayload(GSON.toJson(
                new SelectionResponse(
                        true, "", "", "", 0, 0, result.remainingPoints(),
                        result.profile().professionId(), String.join(",", result.profile().traitIds())
                )
        )));
        sendSuccessMessage(player, result);
    }

    private static void sendFailure(ServerPlayer player, String errorCode, String argumentOne, String argumentTwo,
                                    int requiredPoints, int availablePoints) {
        PacketDistributor.sendToPlayer(player, new CharacterSelectionResultPayload(GSON.toJson(
                new SelectionResponse(
                        false,
                        errorCode == null ? "invalid_request" : errorCode,
                        argumentOne == null ? "" : argumentOne,
                        argumentTwo == null ? "" : argumentTwo,
                        requiredPoints,
                        availablePoints,
                        0,
                        "",
                        ""
                )
        )));
    }

    private static void sendSuccessMessage(ServerPlayer player, CharacterCatalog.SelectionResult result) {
        CharacterCatalog.Snapshot snapshot = CharacterCatalog.snapshot();
        CharacterProfession profession = CharacterCatalog.parseId(result.profile().professionId())
                .map(snapshot.professions()::get)
                .orElse(null);
        Component professionName = profession == null
                ? Component.literal(result.profile().professionId())
                : Component.translatable(profession.nameKey());

        MutableComponent traitSummary = Component.empty();
        if (result.profile().traitIds().isEmpty()) {
            traitSummary.append(Component.translatable("message.aftermath.character.no_traits"));
        } else {
            boolean first = true;
            for (String traitId : result.profile().traitIds()) {
                if (!first) {
                    traitSummary.append(Component.literal(", "));
                }
                first = false;
                Component traitName = CharacterCatalog.parseId(traitId)
                        .map(snapshot.traits()::get)
                        .map(trait -> Component.translatable(trait.nameKey()))
                        .orElseGet(() -> Component.literal(traitId));
                traitSummary.append(traitName);
            }
        }

        player.sendSystemMessage(Component.translatable(
                "message.aftermath.character.choose_success",
                professionName,
                traitSummary,
                result.remainingPoints()
        ));
    }

    public record CatalogView(List<ProfessionView> professions, List<TraitView> traits) {}
    public record ProfessionView(
            String id, String nameKey, String descriptionKey, int startingPoints, List<String> allowedTraits
    ) {}
    public record TraitView(
            String id, String nameKey, String descriptionKey, String category,
            int pointCost, int pointCredit, List<String> conflicts
    ) {}
    public record SelectionResponse(
            boolean accepted, String errorCode, String argumentOne, String argumentTwo,
            int requiredPoints, int availablePoints, int remainingPoints,
            String professionId, String traitIdsCsv
    ) {}
}
