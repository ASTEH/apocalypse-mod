package dev.aftermath.learning;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class TrainingManualItem extends Item {
    private final ResourceLocation lessonId;

    public TrainingManualItem(ResourceLocation lessonId, Properties properties) {
        super(properties);
        this.lessonId = lessonId;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            LearningService.startReading(serverPlayer, lessonId, hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
