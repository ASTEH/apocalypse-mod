package dev.aftermath.content;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;

/** A placeable electronic noise source driven by the shared NoiseService. */
public final class NoiseDecoyBlock extends Block implements EntityBlock {
    private final ResourceLocation noiseEvent;
    private final int pulseIntervalTicks;

    public NoiseDecoyBlock(Properties properties, ResourceLocation noiseEvent, int pulseIntervalTicks) {
        super(properties);
        this.noiseEvent = noiseEvent;
        this.pulseIntervalTicks = Math.max(20, pulseIntervalTicks);
    }

    public ResourceLocation noiseEvent() {
        return noiseEvent;
    }

    public int pulseIntervalTicks() {
        return pulseIntervalTicks;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NoiseDecoyBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != AftermathContent.NOISE_DECOY_BLOCK_ENTITY.get()) return null;
        return (tickLevel, pos, blockState, blockEntity) ->
                ((NoiseDecoyBlockEntity) blockEntity).serverTick();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof NoiseDecoyBlockEntity decoy)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!dev.aftermath.config.AftermathServerConfig.ENABLE_NOISE_DECOYS.get()) {
            player.sendSystemMessage(Component.translatable("message.aftermath.decoy.disabled"));
            return InteractionResult.CONSUME;
        }

        NoiseDecoyBlockEntity.ToggleResult result = decoy.toggle();
        switch (result) {
            case STARTED -> player.sendSystemMessage(Component.translatable("message.aftermath.decoy.started"));
            case STOPPED -> player.sendSystemMessage(Component.translatable("message.aftermath.decoy.stopped"));
            case EMPTY -> player.sendSystemMessage(Component.translatable("message.aftermath.decoy.empty"));
        }
        return InteractionResult.CONSUME;
    }
}
