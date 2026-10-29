package dev.aftermath.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.aftermath.AftermathMod;
import dev.aftermath.character.AftermathAttachments;
import dev.aftermath.character.CharacterCatalog;
import dev.aftermath.character.CharacterProfile;
import dev.aftermath.character.CharacterProfession;
import dev.aftermath.character.CharacterTrait;
import dev.aftermath.config.AftermathServerConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;

public final class AftermathCommands {
    private AftermathCommands() {}

    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("aftermath")
                        .then(Commands.literal("status").executes(context -> {
                            var source = context.getSource();
                            source.sendSuccess(() -> Component.literal(
                                    "Aftermath 0.1.0 | character=" + AftermathServerConfig.ENABLE_CHARACTER_CREATION.get()
                                            + ", weaponXP=" + AftermathServerConfig.ENABLE_WEAPON_PROGRESSION.get()
                                            + ", learning=" + AftermathServerConfig.ENABLE_LEARNING.get()
                                            + ", noise=" + AftermathServerConfig.ENABLE_NOISE_EVENTS.get()
                                            + ", hordeAI=" + AftermathServerConfig.ENABLE_HORDE_BEHAVIOR.get()),
                                    false);
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(Commands.literal("about").executes(context -> {
                            context.getSource().sendSuccess(() -> Component.literal(
                                    "Aftermath is an early-development survival framework. Use /aftermath status to inspect server settings."),
                                    false);
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(Commands.literal("character")
                                .then(Commands.literal("list").executes(context -> listCharacters(context.getSource())))
                                .then(Commands.literal("show").executes(AftermathCommands::showCharacter))
                                .then(Commands.literal("choose")
                                        .then(Commands.argument("profession", StringArgumentType.word())
                                                .executes(context -> chooseCharacter(context, ""))
                                                .then(Commands.argument("traits", StringArgumentType.greedyString())
                                                        .executes(context -> chooseCharacter(context,
                                                                StringArgumentType.getString(context, "traits")))))))
        );

        AftermathMod.LOGGER.debug("Registered /aftermath commands.");
    }

    private static int listCharacters(CommandSourceStack source) {
        CharacterCatalog.Snapshot snapshot = CharacterCatalog.snapshot();
        if (!snapshot.isLoaded()) {
            source.sendFailure(Component.translatable("message.aftermath.character.error.definitions_not_loaded"));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("message.aftermath.character.professions_header"), false);
        for (CharacterProfession profession : snapshot.professions().values()) {
            source.sendSuccess(() -> Component.translatable(
                    "message.aftermath.character.profession_line",
                    Component.translatable(profession.nameKey()),
                    profession.id().toString(),
                    profession.startingPoints()), false);
        }

        source.sendSuccess(() -> Component.translatable("message.aftermath.character.traits_header"), false);
        for (CharacterTrait trait : snapshot.traits().values()) {
            boolean positive = trait.category() == CharacterTrait.Category.POSITIVE;
            String budgetKey = positive
                    ? "message.aftermath.character.points_cost"
                    : "message.aftermath.character.points_credit";
            int points = positive ? trait.pointCost() : trait.pointCredit();
            source.sendSuccess(() -> Component.translatable(
                    "message.aftermath.character.trait_line",
                    Component.translatable(trait.nameKey()),
                    trait.id().toString(),
                    Component.translatable(budgetKey),
                    points), false);
        }

        source.sendSuccess(() -> Component.translatable("message.aftermath.character.choose_hint"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int showCharacter(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CharacterProfile profile = player.getData(AftermathAttachments.CHARACTER_PROFILE);
        if (!profile.complete()) {
            context.getSource().sendFailure(Component.translatable("message.aftermath.character.no_profile"));
            return 0;
        }

        CharacterCatalog.Snapshot snapshot = CharacterCatalog.snapshot();
        Component professionName = CharacterCatalog.parseId(profile.professionId())
                .map(snapshot.professions()::get)
                .map(profession -> Component.translatable(profession.nameKey()))
                .orElseGet(() -> Component.literal(profile.professionId()));

        MutableComponent traits;
        if (profile.traitIds().isEmpty()) {
            traits = Component.translatable("message.aftermath.character.no_traits");
        } else {
            traits = Component.empty();
            boolean first = true;
            for (String traitId : profile.traitIds()) {
                if (!first) {
                    traits.append(Component.literal(", "));
                }
                first = false;
                Component name = CharacterCatalog.parseId(traitId)
                        .map(snapshot.traits()::get)
                        .map(trait -> Component.translatable(trait.nameKey()))
                        .orElseGet(() -> Component.literal(traitId));
                traits.append(name);
            }
        }

        MutableComponent finalTraits = traits;
        context.getSource().sendSuccess(() -> Component.translatable(
                "message.aftermath.character.profile_summary", professionName, finalTraits), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int chooseCharacter(CommandContext<CommandSourceStack> context, String rawTraits)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        CharacterProfile currentProfile = player.getData(AftermathAttachments.CHARACTER_PROFILE);

        if (currentProfile.complete()
                && (!AftermathServerConfig.ALLOW_CHARACTER_RESELECTION.get() || !source.hasPermission(2))) {
            source.sendFailure(Component.translatable("message.aftermath.character.error.reselection_blocked"));
            return 0;
        }

        List<String> selectedTraits = new ArrayList<>();
        if (rawTraits != null && !rawTraits.isBlank() && !rawTraits.trim().equalsIgnoreCase("none")) {
            for (String token : rawTraits.trim().split("[,\\s]+")) {
                if (!token.isBlank()) {
                    selectedTraits.add(token);
                }
            }
        }

        String professionInput = StringArgumentType.getString(context, "profession");
        CharacterCatalog.SelectionResult result =
                CharacterCatalog.validateSelection(professionInput, selectedTraits);
        if (!result.accepted()) {
            source.sendFailure(selectionError(result));
            return 0;
        }

        player.setData(AftermathAttachments.CHARACTER_PROFILE, result.profile());
        CharacterCatalog.Snapshot snapshot = CharacterCatalog.snapshot();
        CharacterProfession profession = CharacterCatalog.parseId(result.profile().professionId())
                .map(snapshot.professions()::get)
                .orElse(null);
        Component professionName = profession == null
                ? Component.literal(result.profile().professionId())
                : Component.translatable(profession.nameKey());

        String traitSummary = result.profile().traitIds().isEmpty()
                ? "none"
                : String.join(", ", result.profile().traitIds());
        source.sendSuccess(() -> Component.translatable(
                "message.aftermath.character.choose_success",
                professionName,
                traitSummary,
                result.remainingPoints()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static Component selectionError(CharacterCatalog.SelectionResult result) {
        String key = "message.aftermath.character.error." + result.errorCode();
        return switch (result.errorCode()) {
            case "unknown_profession", "unknown_trait", "duplicate_trait" ->
                    Component.translatable(key, result.argumentOne());
            case "trait_not_allowed", "trait_conflict" ->
                    Component.translatable(key, result.argumentOne(), result.argumentTwo());
            case "insufficient_points" ->
                    Component.translatable(key, result.requiredPoints(), result.availablePoints());
            default -> Component.translatable(key);
        };
    }
}
