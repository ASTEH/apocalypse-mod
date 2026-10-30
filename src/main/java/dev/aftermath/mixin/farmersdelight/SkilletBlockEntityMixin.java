package dev.aftermath.mixin.farmersdelight;

import dev.aftermath.learning.compat.FarmersDelightLearningHooks;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Awards Cooking XP after the skillet has spawned its cooked output and consumed one input. */
@Pseudo
@Mixin(targets = "vectorwing.farmersdelight.common.block.entity.SkilletBlockEntity", remap = false)
public abstract class SkilletBlockEntityMixin {
    @Inject(
            method = "cookAndOutputItems(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/neoforged/neoforge/items/ItemStackHandler;extractItem(IIZ)Lnet/minecraft/world/item/ItemStack;",
                    shift = At.Shift.AFTER
            ),
            remap = false
    )
    private void aftermath$awardSkilletXp(
            net.minecraft.world.item.ItemStack input,
            net.minecraft.world.level.Level level,
            CallbackInfo callback
    ) {
        FarmersDelightLearningHooks.awardAt((BlockEntity) (Object) this);
    }
}
