package dev.aftermath.mixin.create;

import dev.aftermath.learning.compat.CreateLearningHooks;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Covers completed Create millstone recipes. */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.kinetics.millstone.MillstoneBlockEntity", remap = false)
public abstract class MillstoneBlockEntityMixin {
    @Inject(method = "process()V", at = @At("TAIL"), remap = false)
    private void aftermath$awardMillstoneProcessingXp(CallbackInfo callbackInfo) {
        CreateLearningHooks.awardAt((BlockEntity) (Object) this);
    }
}
