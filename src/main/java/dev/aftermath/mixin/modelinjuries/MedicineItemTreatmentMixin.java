package dev.aftermath.mixin.modelinjuries;

import dev.aftermath.medical.ModelInjuriesSkillBridge;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Scopes the actor while a Model Injuries medicine item is consumed. */
@Mixin(targets = "dev.modelinjuries.item.MedicineItem", remap = false)
public abstract class MedicineItemTreatmentMixin {
    @Inject(method = "finishUsingItem", at = @At("HEAD"), remap = false)
    private void aftermath$beginMedicine(ItemStack stack, Level level, LivingEntity entity,
                                         CallbackInfoReturnable<ItemStack> callbackInfo) {
        ModelInjuriesSkillBridge.hookEntered("medicine item");
        ModelInjuriesSkillBridge.begin(entity);
    }

    @Inject(method = "finishUsingItem", at = @At("RETURN"), remap = false)
    private void aftermath$endMedicine(ItemStack stack, Level level, LivingEntity entity,
                                       CallbackInfoReturnable<ItemStack> callbackInfo) {
        ModelInjuriesSkillBridge.end();
    }
}
