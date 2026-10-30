package dev.aftermath.learning;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/** Generic data-driven VHS cassette. The server validates its embedded tape ID against datapack data. */
public final class TrainingVhsTapeItem extends Item {
    public TrainingVhsTapeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            CustomData data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if (data == null) {
                serverPlayer.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("message.aftermath.learning.vhs_invalid"), true);
            } else {
                CompoundTag tag = data.copyTag();
                ResourceLocation tapeId = tag.contains("VhsTapeId")
                        ? ResourceLocation.tryParse(tag.getString("VhsTapeId")) : null;
                if (tapeId == null) {
                    serverPlayer.displayClientMessage(
                            net.minecraft.network.chat.Component.translatable("message.aftermath.learning.vhs_invalid"), true);
                } else {
                    LearningService.startVhsTape(serverPlayer, tapeId, hand);
                }
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}