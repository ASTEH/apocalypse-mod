package dev.aftermath.learning.compat;

import dev.aftermath.learning.LearningService;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Bridge used by optional Farmer's Delight mixins without linking its classes into core code. */
public final class FarmersDelightLearningHooks {
    private FarmersDelightLearningHooks() {}

    public static void awardAt(BlockEntity machine) {
        if (machine == null || machine.getLevel() == null) return;
        LearningService.awardFarmersDelightCookingXp(machine.getLevel(), machine.getBlockPos());
    }
}
