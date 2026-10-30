package dev.aftermath.mixin.create;

import dev.aftermath.learning.compat.CreateLearningHooks;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Awards mechanics XP after an item entity was successfully processed by a Create fan. */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.kinetics.fan.processing.FanProcessing", remap = false)
public abstract class FanProcessingMixin {
    @Inject(
            method = "applyProcessing(Lnet/minecraft/world/entity/item/ItemEntity;Lcom/simibubi/create/content/kinetics/fan/processing/FanProcessingType;)Z",
            at = @At("RETURN"),
            remap = false
    )
    private static void aftermath$awardFanProcessingXp(
            ItemEntity entity,
            @Coerce Object processingType,
            CallbackInfoReturnable<Boolean> callback
    ) {
        if (Boolean.TRUE.equals(callback.getReturnValue())) {
            CreateLearningHooks.awardAt(entity);
        }
    }
}
