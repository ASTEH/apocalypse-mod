package dev.aftermath.mixin.modelinjuries;

import dev.aftermath.AftermathMod;
import dev.aftermath.medical.ModelInjuriesSkillBridge;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Hooks Model Injuries' MedicineItem success path. A medicine counts only if
 * InjuryState.takeMedicine(...) accepts it; rejected/already-active doses grant no XP.
 */
@Mixin(targets = "dev.modelinjuries.item.MedicineItem", remap = false)
public abstract class MedicineItemTreatmentMixin {
    private static boolean aftermath$warnedAboutMedicineApi;

    @Inject(method = "finishUsingItem", at = @At("HEAD"), remap = false)
    private void aftermath$beginMedicine(ItemStack stack, Level level, LivingEntity entity,
                                         CallbackInfoReturnable<ItemStack> callbackInfo) {
        ModelInjuriesSkillBridge.begin(entity);
    }

    @Redirect(
            method = "finishUsingItem",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/modelinjuries/core/InjuryState;takeMedicine(Ldev/modelinjuries/core/Medicine;)Z",
                    remap = false),
            remap = false)
    private boolean aftermath$onMedicineResult(@Coerce Object injuryState, @Coerce Object medicine) {
        boolean successful = invokeTakeMedicine(injuryState, medicine);
        if (successful) ModelInjuriesSkillBridge.onSuccessfulProcedure();
        return successful;
    }

    @Inject(method = "finishUsingItem", at = @At("RETURN"), remap = false)
    private void aftermath$endMedicine(ItemStack stack, Level level, LivingEntity entity,
                                       CallbackInfoReturnable<ItemStack> callbackInfo) {
        ModelInjuriesSkillBridge.end();
    }

    private static boolean invokeTakeMedicine(Object injuryState, Object medicine) {
        try {
            Method method = injuryState.getClass().getMethod("takeMedicine", medicine.getClass());
            return (boolean) method.invoke(injuryState, medicine);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Model Injuries medicine application failed.", cause);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (!aftermath$warnedAboutMedicineApi) {
                aftermath$warnedAboutMedicineApi = true;
                AftermathMod.LOGGER.error(
                        "Could not invoke Model Injuries InjuryState.takeMedicine(...); medicine XP bridge is disabled.",
                        exception);
            }
            return false;
        }
    }
}
