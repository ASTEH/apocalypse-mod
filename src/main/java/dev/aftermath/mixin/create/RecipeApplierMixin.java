package dev.aftermath.mixin.create;

import dev.aftermath.learning.compat.CreateLearningHooks;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Covers item-entity recipe processing, including mechanical pressing on belts. */
@Pseudo
@Mixin(targets = "com.simibubi.create.foundation.recipe.RecipeApplier", remap = false)
public abstract class RecipeApplierMixin {
    @Inject(
            method = "applyRecipeOn(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/crafting/Recipe;Z)V",
            at = @At("TAIL"),
            remap = false
    )
    private static void aftermath$awardCreateProcessingXp(
            ItemEntity processedItem,
            Recipe<?> recipe,
            boolean returnProcessingRemainder,
            CallbackInfo callbackInfo
    ) {
        CreateLearningHooks.awardAt(processedItem);
    }
}
