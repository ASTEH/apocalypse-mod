package dev.aftermath.content;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Installs three minutes of charge in a radio or alarm decoy. */
public final class NoiseBatteryItem extends Item {
    public NoiseBatteryItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!dev.aftermath.config.AftermathServerConfig.ENABLE_NOISE_DECOYS.get()) {
            if (!level.isClientSide && context.getPlayer() != null) {
                context.getPlayer().sendSystemMessage(Component.translatable("message.aftermath.decoy.disabled"));
            }
            return InteractionResult.CONSUME;
        }
        if (!(level.getBlockEntity(context.getClickedPos()) instanceof NoiseDecoyBlockEntity decoy)) {
            return super.useOn(context);
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;

        int accepted = decoy.charge(NoiseDecoyBlockEntity.TICKS_PER_BATTERY);
        if (accepted <= 0) {
            if (context.getPlayer() != null) {
                context.getPlayer().sendSystemMessage(Component.translatable("message.aftermath.decoy.battery_full"));
            }
            return InteractionResult.CONSUME;
        }

        ItemStack stack = context.getItemInHand();
        if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) stack.shrink(1);
        if (context.getPlayer() != null) {
            context.getPlayer().sendSystemMessage(Component.translatable(
                    "message.aftermath.decoy.battery_added", accepted / 20 / 60));
        }
        return InteractionResult.CONSUME;
    }
}
