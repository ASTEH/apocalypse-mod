package dev.aftermath.mixin.modelinjuries;

import dev.aftermath.medical.ModelInjuriesSkillBridge;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Scopes the actor while the classic self-treatment completion path runs. */
@Mixin(targets = "dev.modelinjuries.InjuryEvents", remap = false)
public abstract class InjuryTreatmentMixin {
    @Inject(method = "finishTreatment", at = @At("HEAD"), remap = false)
    private static void aftermath$beginTreatment(Player player, ItemStack stack,
                                                  CallbackInfoReturnable<ItemStack> callbackInfo) {
        ModelInjuriesSkillBridge.hookEntered("standard self-treatment");
        ModelInjuriesSkillBridge.begin(player);
    }

    @Inject(method = "finishTreatment", at = @At("RETURN"), remap = false)
    private static void aftermath$endTreatment(Player player, ItemStack stack,
                                                CallbackInfoReturnable<ItemStack> callbackInfo) {
        ModelInjuriesSkillBridge.end();
    }
}
