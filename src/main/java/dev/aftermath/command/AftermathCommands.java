package dev.aftermath.command;

import dev.aftermath.AftermathMod;
import dev.aftermath.config.AftermathServerConfig;
import dev.aftermath.character.WeaponProgressionService;
import dev.aftermath.learning.LearningService;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Safe diagnostic commands for the initial bootstrap. */
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
                            return 1;
                        }))
                        .then(Commands.literal("about").executes(context -> {
                            context.getSource().sendSuccess(() -> Component.literal(
                                    "Aftermath is an early-development survival framework. Use /aftermath status to inspect server settings."),
                                    false);
                            return 1;
                        }))
                        .then(Commands.literal("weapons").executes(context -> {
                            var player = context.getSource().getPlayerOrException();
                            context.getSource().sendSuccess(
                                    () -> WeaponProgressionService.buildStatusMessage(player), false);
                            return 1;
                        }))
                        .then(Commands.literal("learning").executes(context -> {
                            var player = context.getSource().getPlayerOrException();
                            context.getSource().sendSuccess(
                                    () -> LearningService.buildStatusMessage(player), false);
                            return 1;
                        }))
        );

        AftermathMod.LOGGER.debug("Registered /aftermath commands.");
    }
}
