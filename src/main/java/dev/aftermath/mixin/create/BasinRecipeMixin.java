package dev.aftermath.mixin.create;

import dev.aftermath.learning.compat.CreateLearningHooks;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Covers Create basin processing used by mixers and presses operating over a basin. */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.processing.basin.BasinRecipe", remap = false)
public abstract class BasinRecipeMixin {
    @Inject(
            method = "apply(Lcom/simibubi/create/content/processing/basin/BasinBlockEntity;Lnet/minecraft/world/item/crafting/Recipe;)Z",
            at = @At("RETURN"),
            remap = false
    )
    private static void aftermath$awardBasinProcessingXp(
            @Coerce Object basin,
            Recipe<?> recipe,
            CallbackInfoReturnable<Boolean> callback
    ) {
        if (Boolean.TRUE.equals(callback.getReturnValue())) {
            CreateLearningHooks.awardAt((BlockEntity) basin);
        }
    }
}
