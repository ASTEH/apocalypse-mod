package dev.aftermath.content;

import dev.aftermath.zombie.ZombieCamouflageService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import java.util.List;
import net.minecraft.world.level.Level;

/** One-use organic camouflage; it reduces target acquisition but never grants invisibility. */
public final class ZombieDisguiseItem extends Item {
    public ZombieDisguiseItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<net.minecraft.network.chat.Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.aftermath.zombie_disguise"));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof net.minecraft.server.level.ServerLevel
                && player instanceof ServerPlayer serverPlayer) {
            if (!dev.aftermath.config.AftermathServerConfig.ENABLE_ZOMBIE_CAMOUFLAGE.get()) {
                return InteractionResultHolder.fail(stack);
            }
            if (ZombieCamouflageService.isActive(serverPlayer)) {
                serverPlayer.sendSystemMessage(Component.translatable("message.aftermath.camouflage.already_active"));
                return InteractionResultHolder.fail(stack);
            }
            ZombieCamouflageService.activate(serverPlayer);
            if (!serverPlayer.getAbilities().instabuild) stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
