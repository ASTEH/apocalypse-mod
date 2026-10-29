package dev.aftermath.command;

import dev.aftermath.AftermathMod;
import dev.aftermath.config.AftermathServerConfig;
import dev.aftermath.character.WeaponProgressionService;
import dev.aftermath.learning.LearningService;
import dev.aftermath.noise.HordeDirector;
import dev.aftermath.noise.NoiseService;
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
                        .then(Commands.literal("horde")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.literal("status").executes(context -> {
                                    Component enabled = Component.translatable(HordeDirector.isHordeBehaviorEnabled()
                                            ? "message.aftermath.horde.enabled" : "message.aftermath.horde.disabled");
                                    Component mode = Component.translatable(HordeDirector.hasRuntimeBehaviorOverride()
                                            ? "message.aftermath.horde.mode_temporary" : "message.aftermath.horde.mode_config");
                                    context.getSource().sendSuccess(() -> Component.translatable(
                                            "message.aftermath.horde.status", enabled, mode), false);
                                    return 1;
                                }))
                                .then(Commands.literal("enable").executes(context -> {
                                    HordeDirector.setRuntimeBehaviorOverride(true);
                                    context.getSource().sendSuccess(() -> Component.translatable(
                                            "message.aftermath.horde.enabled_temporary"), true);
                                    return 1;
                                }))
                                .then(Commands.literal("disable").executes(context -> {
                                    HordeDirector.setRuntimeBehaviorOverride(false);
                                    context.getSource().sendSuccess(() -> Component.translatable(
                                            "message.aftermath.horde.disabled_temporary"), true);
                                    return 1;
                                }))
                                .then(Commands.literal("reset").executes(context -> {
                                    HordeDirector.resetRuntimeBehaviorOverride();
                                    context.getSource().sendSuccess(() -> Component.translatable(
                                            "message.aftermath.horde.reset",
                                            HordeDirector.isHordeBehaviorEnabled()
                                                    ? Component.translatable("message.aftermath.horde.enabled")
                                                    : Component.translatable("message.aftermath.horde.disabled")), false);
                                    return 1;
                                }))
                        )
                        .then(Commands.literal("noise")
                                .then(Commands.literal("test").executes(context -> {
                                    var player = context.getSource().getPlayerOrException();
                                    NoiseService.NoiseResult result = NoiseService.emitNoise(
                                            player.serverLevel(), player.position(),
                                            NoiseService.gunshotEventId(), player.getUUID());
                                    Component feedback = switch (result.status()) {
                                        case EMITTED -> Component.translatable(
                                                "message.aftermath.noise.emitted",
                                                result.affectedEntities(), result.groupedFollowers());
                                        case DISABLED -> Component.translatable("message.aftermath.noise.disabled");
                                        case UNKNOWN_EVENT -> Component.translatable("message.aftermath.noise.unknown_event");
                                        case COOLDOWN -> Component.translatable("message.aftermath.noise.cooldown");
                                    };
                                    if (result.status() == NoiseService.Status.EMITTED) {
                                        context.getSource().sendSuccess(() -> feedback, false);
                                        return 1;
                                    }
                                    context.getSource().sendFailure(feedback);
                                    return 0;
                                })))
        );

        AftermathMod.LOGGER.debug("Registered /aftermath commands.");
    }
}
