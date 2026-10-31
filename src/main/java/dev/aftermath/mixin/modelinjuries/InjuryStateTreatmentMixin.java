package dev.aftermath.mixin.modelinjuries;

import dev.aftermath.medical.ModelInjuriesSkillBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hooks Model Injuries' authoritative success gates, independent of the UI that called them. */
@Mixin(targets = "dev.modelinjuries.core.InjuryState", remap = false)
public abstract class InjuryStateTreatmentMixin {
    @Inject(method = "treat", at = @At("RETURN"), remap = false)
    private void aftermath$afterTreatment(@Coerce Object region, @Coerce Object treatment,
                                          CallbackInfoReturnable<Boolean> callbackInfo) {
        if (Boolean.TRUE.equals(callbackInfo.getReturnValue())) {
            ModelInjuriesSkillBridge.onSuccessfulProcedure();
        }
    }

    @Inject(method = "takeMedicine", at = @At("RETURN"), remap = false)
    private void aftermath$afterMedicine(@Coerce Object medicine,
                                         CallbackInfoReturnable<Boolean> callbackInfo) {
        if (Boolean.TRUE.equals(callbackInfo.getReturnValue())) {
            ModelInjuriesSkillBridge.onSuccessfulProcedure();
        }
    }
}
