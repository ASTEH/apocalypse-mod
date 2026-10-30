package dev.aftermath.mixin.farmersdelight;

import dev.aftermath.learning.compat.FarmersDelightLearningHooks;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Awards Cooking XP after Farmer's Delight has inserted a completed meal into the pot. */
@Pseudo
@Mixin(targets = "vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity", remap = false)
public abstract class CookingPotBlockEntityMixin {
    @Inject(
            method = "processCooking(Lnet/minecraft/world/item/crafting/RecipeHolder;Lvectorwing/farmersdelight/common/block/entity/CookingPotBlockEntity;)Z",
            at = @At("RETURN"),
            remap = false
    )
    private void aftermath$awardCookingPotXp(
            @Coerce Object recipe,
            @Coerce Object cookingPot,
            CallbackInfoReturnable<Boolean> callback
    ) {
        if (Boolean.TRUE.equals(callback.getReturnValue())) {
            FarmersDelightLearningHooks.awardAt((BlockEntity) (Object) this);
        }
    }
}
