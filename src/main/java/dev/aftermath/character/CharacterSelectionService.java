package dev.aftermath.character;

import dev.aftermath.AftermathMod;
import dev.aftermath.config.AftermathServerConfig;
import dev.aftermath.network.CharacterSelectionResultPayload;
import dev.aftermath.network.SelectCharacterPayload;
import dev.aftermath.network.TraitStatePayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Validates and applies character choices on the logical server only. */
public final class CharacterSelectionService {
    public static void handleSelection(SelectCharacterPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;

        if (!AftermathServerConfig.ENABLE_CHARACTER_CREATION.get()) {
            respond(player, false, "screen.aftermath.character.error.disabled");
            return;
        }

        CharacterProfile existing = player.getData(AftermathAttachments.CHARACTER_PROFILE);
        if (existing.created()) {
            respond(player, false, "screen.aftermath.character.error.already_created");
            return;
        }

        CharacterDefinitions.Snapshot definitions = CharacterDefinitions.snapshot();
        ResourceLocation professionId = ResourceLocation.tryParse(payload.professionId());
        CharacterDefinitions.ProfessionDefinition profession =
                professionId == null ? null : definitions.professions().get(professionId);
        if (profession == null) {
            respond(player, false, "screen.aftermath.character.error.invalid_profession");
            return;
        }

        String csv = payload.traitIdsCsv() == null ? "" : payload.traitIdsCsv();
        String[] rawIds = csv.isBlank() ? new String[0] : csv.split(",", -1);
        if (rawIds.length > 32) {
            respond(player, false, "screen.aftermath.character.error.too_many_traits");
            return;
        }

        Set<ResourceLocation> selectedIds = new LinkedHashSet<>();
        List<CharacterDefinitions.TraitDefinition> selectedTraits = new ArrayList<>();
        for (ResourceLocation grantedId : profession.grantedTraits()) {
            CharacterDefinitions.TraitDefinition granted = definitions.traits().get(grantedId);
            if (granted == null || !selectedIds.add(grantedId)) {
                respond(player, false, "screen.aftermath.character.error.invalid_trait");
                return;
            }
            selectedTraits.add(granted);
        }
        for (String rawId : rawIds) {
            ResourceLocation traitId = ResourceLocation.tryParse(rawId);
            if (traitId == null || !selectedIds.add(traitId)) {
                respond(player, false, "screen.aftermath.character.error.invalid_trait");
                return;
            }
            if (!profession.allowedTraits().isEmpty() && !profession.allowedTraits().contains(traitId)) {
                respond(player, false, "screen.aftermath.character.error.trait_not_allowed");
                return;
            }
            CharacterDefinitions.TraitDefinition trait = definitions.traits().get(traitId);
            if (trait == null) {
                respond(player, false, "screen.aftermath.character.error.invalid_trait");
                return;
            }
            if (trait.requiresProfession() != null && !trait.requiresProfession().equals(profession.id())) {
                respond(player, false, "screen.aftermath.character.error.profession_required");
                return;
            }
            selectedTraits.add(trait);
        }

        for (CharacterDefinitions.TraitDefinition trait : selectedTraits) {
            for (ResourceLocation conflict : trait.conflicts()) {
                if (selectedIds.contains(conflict)) {
                    respond(player, false, "screen.aftermath.character.error.conflicting_traits");
                    return;
                }
            }
        }

        int remainingPoints = profession.startingPoints();
        for (CharacterDefinitions.TraitDefinition trait : selectedTraits) {
            if (profession.grantedTraits().contains(trait.id())) continue;
            remainingPoints += trait.positive() ? -trait.pointCost() : trait.pointCredit();
        }
        if (remainingPoints < 0) {
            respond(player, false, "screen.aftermath.character.error.not_enough_points");
            return;
        }

        List<String> savedTraits = selectedTraits.stream().map(trait -> trait.id().toString()).toList();
        player.setData(AftermathAttachments.CHARACTER_PROFILE,
                new CharacterProfile(profession.id().toString(), savedTraits, true, profession.skillBonuses()));
        PacketDistributor.sendToPlayer(player, new TraitStatePayload(String.join(",", savedTraits)));

        CharacterEffectApplier.apply(player, selectedTraits);
        grantStartingItems(player, profession);
        AftermathMod.LOGGER.info("Player {} created an Aftermath character: profession={}, traits={}",
                player.getGameProfile().getName(), profession.id(), savedTraits);
        respond(player, true, "screen.aftermath.character.success");
    }

    public static void reapplyProfile(ServerPlayer player) {
        CharacterProfile profile = player.getData(AftermathAttachments.CHARACTER_PROFILE);
        if (!profile.created()) return;
        List<CharacterDefinitions.TraitDefinition> traits = profile.traitIds().stream()
                .map(ResourceLocation::tryParse)
                .filter(id -> id != null)
                .map(CharacterDefinitions.snapshot().traits()::get)
                .filter(trait -> trait != null)
                .toList();
        CharacterEffectApplier.apply(player, traits);
    }

    private static void grantStartingItems(ServerPlayer player,
                                           CharacterDefinitions.ProfessionDefinition profession) {
        for (CharacterDefinitions.StartingItem startingItem : profession.startingItems()) {
            Item item = BuiltInRegistries.ITEM.getOptional(startingItem.itemId()).orElse(null);
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                AftermathMod.LOGGER.warn("Skipping unknown starting item {} for profession {}.",
                        startingItem.itemId(), profession.id());
                continue;
            }
            ItemStack stack = new ItemStack(item, startingItem.count());
            if (!player.getInventory().add(stack) && !stack.isEmpty()) {
                player.drop(stack, false);
            }
        }
    }

    private static void respond(ServerPlayer player, boolean success, String messageKey) {
        PacketDistributor.sendToPlayer(player, new CharacterSelectionResultPayload(success, messageKey));
    }

    private CharacterSelectionService() {}
}
