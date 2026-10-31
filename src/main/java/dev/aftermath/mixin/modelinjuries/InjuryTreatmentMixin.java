package dev.aftermath.mixin.modelinjuries;

import dev.aftermath.AftermathMod;
import dev.aftermath.medical.ModelInjuriesSkillBridge;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Hooks Model Injuries' actual TreatmentItem success path.
 * InjuryEvents.finishTreatment only calls InjuryState.treat(...) after validating the
 * selected region, active treatment time, care permission, held item and interaction hand.
 */
@Mixin(targets = "dev.modelinjuries.InjuryEvents", remap = false)
public abstract class InjuryTreatmentMixin {
    private static boolean aftermath$warnedAboutTreatApi;

    @Inject(method = "finishTreatment", at = @At("HEAD"), remap = false)
    private static void aftermath$beginTreatment(Player player, ItemStack stack,
                                                  CallbackInfoReturnable<ItemStack> callbackInfo) {
        ModelInjuriesSkillBridge.begin(player);
    }

    @Redirect(
            method = "finishTreatment",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/modelinjuries/core/InjuryState;treat(Ldev/modelinjuries/core/Region;Ldev/modelinjuries/core/InjuryState$Treatment;)Z",
                    remap = false),
            remap = false)
    private static boolean aftermath$onTreatmentResult(
            @Coerce Object injuryState, @Coerce Object region, @Coerce Object treatment) {
        boolean successful = invokeTreat(injuryState, region, treatment);
        if (successful) ModelInjuriesSkillBridge.onSuccessfulProcedure();
        return successful;
    }

    @Inject(method = "finishTreatment", at = @At("RETURN"), remap = false)
    private static void aftermath$endTreatment(Player player, ItemStack stack,
                                                CallbackInfoReturnable<ItemStack> callbackInfo) {
        ModelInjuriesSkillBridge.end();
    }

    private static boolean invokeTreat(Object injuryState, Object region, Object treatment) {
        try {
            Method method = injuryState.getClass().getMethod("treat", region.getClass(), treatment.getClass());
            return (boolean) method.invoke(injuryState, region, treatment);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Model Injuries treatment failed.", cause);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (!aftermath$warnedAboutTreatApi) {
                aftermath$warnedAboutTreatApi = true;
                AftermathMod.LOGGER.error(
                        "Could not invoke Model Injuries InjuryState.treat(...); treatment XP bridge is disabled.",
                        exception);
            }
            return false;
        }
    }
}
