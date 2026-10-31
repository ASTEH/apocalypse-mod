package dev.aftermath.foraging;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/** Per-dimension persistence for depleted forage spots and recent rainfall. */
public final class ForagingWorldData extends SavedData {
    private static final String DATA_NAME = "aftermath_foraging";
    private static final long REGROWTH_TICKS = 7L * 24_000L;
    private static final long RECENT_RAIN_TICKS = 12_000L;
    private static final Factory<ForagingWorldData> FACTORY =
            new Factory<>(ForagingWorldData::new, ForagingWorldData::load, null);

    private final Map<Long, Long> depletedAt = new HashMap<>();
    private long lastRainAt = Long.MIN_VALUE;

    private ForagingWorldData() {}

    public static ForagingWorldData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public boolean isDepleted(BlockPos pos, long now) {
        Long collectedAt = depletedAt.get(pos.asLong());
        if (collectedAt == null) return false;
        if (now >= collectedAt && now - collectedAt >= REGROWTH_TICKS) {
            depletedAt.remove(pos.asLong());
            setDirty();
            return false;
        }
        return true;
    }

    public void markDepleted(BlockPos pos, long now) {
        depletedAt.put(pos.asLong(), Math.max(0L, now));
        if (depletedAt.size() > 8192) {
            long cutoff = Math.max(0L, now - REGROWTH_TICKS);
            depletedAt.entrySet().removeIf(entry -> entry.getValue() < cutoff);
        }
        setDirty();
    }

    public void observeRain(long now) {
        if (now < 0L || (lastRainAt != Long.MIN_VALUE && now - lastRainAt < 200L)) return;
        lastRainAt = now;
        setDirty();
    }

    public boolean recentlyWet(long now) {
        return lastRainAt != Long.MIN_VALUE && now >= lastRainAt
                && now - lastRainAt <= RECENT_RAIN_TICKS;
    }

    private static ForagingWorldData load(CompoundTag tag, HolderLookup.Provider registries) {
        ForagingWorldData data = new ForagingWorldData();
        data.lastRainAt = tag.contains("last_rain_at", Tag.TAG_LONG)
                ? tag.getLong("last_rain_at") : Long.MIN_VALUE;
        ListTag depleted = tag.getList("depleted", Tag.TAG_COMPOUND);
        for (int i = 0; i < depleted.size() && i < 8192; i++) {
            CompoundTag entry = depleted.getCompound(i);
            if (entry.contains("pos", Tag.TAG_LONG) && entry.contains("time", Tag.TAG_LONG)) {
                data.depletedAt.put(entry.getLong("pos"), Math.max(0L, entry.getLong("time")));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("last_rain_at", lastRainAt);
        ListTag depleted = new ListTag();
        depletedAt.forEach((position, time) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("pos", position);
            entry.putLong("time", time);
            depleted.add(entry);
        });
        tag.put("depleted", depleted);
        return tag;
    }
}
