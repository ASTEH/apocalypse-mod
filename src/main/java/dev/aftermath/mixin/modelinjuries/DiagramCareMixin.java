package dev.aftermath.mixin.modelinjuries;

import dev.aftermath.medical.ModelInjuriesSkillBridge;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scopes the healer during Model Injuries 0.41.0's custom body-diagram care tick. */
@Mixin(targets = "dev.modelinjuries.DiagramCare", remap = false)
public abstract class DiagramCareMixin {
    @Inject(method = "tick", at = @At("HEAD"), remap = false)
    private static void aftermath$beginDiagramCare(ServerPlayer actor, CallbackInfo callbackInfo) {
        ModelInjuriesSkillBridge.hookEntered("body-diagram care UI");
        ModelInjuriesSkillBridge.begin(actor);
    }

    @Inject(method = "tick", at = @At("RETURN"), remap = false)
    private static void aftermath$endDiagramCare(ServerPlayer actor, CallbackInfo callbackInfo) {
        ModelInjuriesSkillBridge.end();
    }
}
