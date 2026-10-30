package dev.aftermath.mixin.create;

import dev.aftermath.learning.compat.CreateLearningHooks;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Awards mechanics XP for actual saw cutting recipes, not package unpacking. */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.kinetics.saw.SawBlockEntity", remap = false)
public abstract class SawBlockEntityMixin {
    @Inject(
            method = "applyRecipe()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/foundation/item/ItemHelper;addToList(Lnet/minecraft/world/item/ItemStack;Ljava/util/List;)V",
                    ordinal = 1,
                    shift = At.Shift.AFTER
            ),
            remap = false
    )
    private void aftermath$awardSawProcessingXp(CallbackInfo callback) {
        CreateLearningHooks.awardAt((BlockEntity) (Object) this);
    }
}
