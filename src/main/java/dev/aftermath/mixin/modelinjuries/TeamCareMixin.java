package dev.aftermath.mixin.modelinjuries;

import dev.aftermath.medical.ModelInjuriesSkillBridge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Scopes the acting healer while Model Injuries completes a teammate-care action. */
@Mixin(targets = "dev.modelinjuries.TeamCare", remap = false)
public abstract class TeamCareMixin {
    @Inject(method = "finish", at = @At("HEAD"), remap = false)
    private static void aftermath$beginTeamCare(ServerPlayer actor, ItemStack supply,
                                                 CallbackInfoReturnable<ItemStack> callbackInfo) {
        ModelInjuriesSkillBridge.hookEntered("team care");
        ModelInjuriesSkillBridge.begin(actor);
    }

    @Inject(method = "finish", at = @At("RETURN"), remap = false)
    private static void aftermath$endTeamCare(ServerPlayer actor, ItemStack supply,
                                               CallbackInfoReturnable<ItemStack> callbackInfo) {
        ModelInjuriesSkillBridge.end();
    }
}
