package dev.aftermath.mixin.firstaid;

import dev.aftermath.AftermathMod;
import dev.aftermath.learning.LearningService;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Bridges completed First Aid New body-part treatments into Aftermath's skill progression.
 * Target/API types are intentionally resolved reflectively so First Aid New remains optional.
 */
@Mixin(targets = "ichttt.mods.firstaid.common.damagesystem.DamageablePart", remap = false)
public abstract class DamageablePartTreatmentMixin {
    private static boolean aftermath$warnedAboutCallback;
    private static boolean aftermath$warnedAboutPlayer;

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lichttt/mods/firstaid/api/healing/ItemHealing;onTreatmentCompleted(Lichttt/mods/firstaid/api/healing/PartHealingContext;)V",
                    remap = false),
            remap = false)
    private void aftermath$onTreatmentCompleted(@Coerce Object healingItem, @Coerce Object context) {
        invokeOriginalCallback(healingItem, context);
        try {
            Object rawPlayer = context.getClass().getMethod("getPlayer").invoke(context);
            if (rawPlayer instanceof ServerPlayer player && !player.level().isClientSide()) {
                LearningService.awardFirstAidXp(player);
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (!aftermath$warnedAboutPlayer) {
                aftermath$warnedAboutPlayer = true;
                AftermathMod.LOGGER.warn(
                        "First Aid New completed a treatment, but Aftermath could not read its player context. "
                                + "First Aid XP will not be awarded for this treatment.",
                        exception);
            }
        }
    }

    private static void invokeOriginalCallback(Object healingItem, Object context) {
        try {
            Class<?> apiType = Class.forName(
                    "ichttt.mods.firstaid.api.healing.ItemHealing",
                    false,
                    healingItem.getClass().getClassLoader());
            Method callback = apiType.getMethod("onTreatmentCompleted", context.getClass());
            callback.invoke(healingItem, context);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("First Aid New treatment callback failed.", cause);
        } catch (ReflectiveOperationException exception) {
            if (!aftermath$warnedAboutCallback) {
                aftermath$warnedAboutCallback = true;
                AftermathMod.LOGGER.error(
                        "Could not invoke the original First Aid New treatment callback. "
                                + "The skill XP bridge is disabled for this treatment.",
                        exception);
            }
        }
    }
}
