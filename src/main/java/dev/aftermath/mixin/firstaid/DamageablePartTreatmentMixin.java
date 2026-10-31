package dev.aftermath.mixin.firstaid;

import dev.aftermath.AftermathMod;
import dev.aftermath.medical.FirstAidSkillBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Fallback for third-party First Aid New items that override onTreatmentCompleted.
 * The public ItemHealing callback mixin remains the primary integration point.
 */
@Mixin(targets = "ichttt.mods.firstaid.common.damagesystem.DamageablePart", remap = false)
public abstract class DamageablePartTreatmentMixin {
    private static boolean aftermath$warnedAboutCallback;

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lichttt/mods/firstaid/api/healing/ItemHealing;onTreatmentCompleted(Lichttt/mods/firstaid/api/healing/PartHealingContext;)V",
                    remap = false),
            remap = false)
    private void aftermath$onTreatmentCompleted(@Coerce Object healingItem, @Coerce Object context) {
        invokeOriginalCallback(healingItem, context);
        // Weak-context deduplication in the bridge prevents a second XP award.
        FirstAidSkillBridge.onTreatmentCompleted(context);
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
                        "Could not invoke the original First Aid New treatment callback.",
                        exception);
            }
        }
    }
}
