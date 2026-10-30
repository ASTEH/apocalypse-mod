package dev.aftermath.learning.compat;

import dev.aftermath.learning.LearningService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Bridge used by optional Create mixins without linking Create classes into core code. */
public final class CreateLearningHooks {
    private CreateLearningHooks() {}

    public static void awardAt(BlockEntity machine) {
        if (machine == null || machine.getLevel() == null) return;
        LearningService.awardCreateProcessingXp(machine.getLevel(), machine.getBlockPos());
    }

    public static void awardAt(ItemEntity processedItem) {
        if (processedItem == null || processedItem.level().isClientSide) return;
        BlockPos position = processedItem.blockPosition();
        LearningService.awardCreateProcessingXp(processedItem.level(), position);
    }
}
