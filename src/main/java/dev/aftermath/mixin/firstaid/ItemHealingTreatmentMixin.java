package dev.aftermath.mixin.firstaid;

import dev.aftermath.medical.FirstAidSkillBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Preferred hook: inject directly into First Aid New's public treatment-completion callback.
 * This avoids depending solely on an internal implementation class or its call site.
 */
@Mixin(targets = "ichttt.mods.firstaid.api.healing.ItemHealing", remap = false)
public abstract class ItemHealingTreatmentMixin {
    @Inject(method = "onTreatmentCompleted", at = @At("RETURN"), remap = false, require = 1)
    private void aftermath$onTreatmentCompleted(@Coerce Object context, CallbackInfo callbackInfo) {
        FirstAidSkillBridge.onTreatmentCompleted(context);
    }
}
