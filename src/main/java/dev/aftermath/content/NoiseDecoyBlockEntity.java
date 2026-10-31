package dev.aftermath.content;

import dev.aftermath.noise.NoiseService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Saves charge, active state and a stable per-device noise source ID. */
public final class NoiseDecoyBlockEntity extends BlockEntity {
    public static final int TICKS_PER_BATTERY = 3_600;
    private static final int MAX_CHARGE_TICKS = 7_200;

    private int batteryTicks;
    private boolean active;
    private UUID sourceId = UUID.randomUUID();

    public NoiseDecoyBlockEntity(BlockPos pos, BlockState state) {
        super(AftermathContent.NOISE_DECOY_BLOCK_ENTITY.get(), pos, state);
    }

    public void serverTick() {
        Level currentLevel = getLevel();
        if (!(currentLevel instanceof ServerLevel level) || level.isClientSide || !active) return;
        if (!(getBlockState().getBlock() instanceof NoiseDecoyBlock device)) {
            active = false;
            setChanged();
            return;
        }

        if (batteryTicks <= 0) {
            active = false;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            return;
        }

        batteryTicks--;
        long gameTime = level.getGameTime();
        if (gameTime % device.pulseIntervalTicks() == 0L) {
            NoiseService.emitNoise(level, Vec3.atCenterOf(worldPosition), device.noiseEvent(), sourceId);
        }

        // Persist charge periodically, not with a disk write every tick.
        if (gameTime % 20L == 0L) setChanged();
        if (batteryTicks == 0) {
            active = false;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public ToggleResult toggle() {
        if (active) {
            active = false;
            setChanged();
            updateBlock();
            return ToggleResult.STOPPED;
        }
        if (batteryTicks <= 0) return ToggleResult.EMPTY;
        active = true;
        setChanged();
        updateBlock();
        return ToggleResult.STARTED;
    }

    public int charge(int ticks) {
        if (ticks <= 0 || batteryTicks >= MAX_CHARGE_TICKS) return 0;
        int accepted = Math.min(ticks, MAX_CHARGE_TICKS - batteryTicks);
        batteryTicks += accepted;
        setChanged();
        updateBlock();
        return accepted;
    }

    public int batteryTicks() {
        return batteryTicks;
    }

    public boolean isActive() {
        return active;
    }

    private void updateBlock() {
        Level currentLevel = getLevel();
        if (currentLevel != null && !currentLevel.isClientSide) {
            currentLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("BatteryTicks", batteryTicks);
        tag.putBoolean("Active", active);
        tag.putUUID("NoiseSource", sourceId);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        batteryTicks = Math.max(0, Math.min(MAX_CHARGE_TICKS, tag.getInt("BatteryTicks")));
        active = tag.getBoolean("Active") && batteryTicks > 0;
        if (tag.hasUUID("NoiseSource")) sourceId = tag.getUUID("NoiseSource");
    }

    public enum ToggleResult {
        STARTED, STOPPED, EMPTY
    }
}
