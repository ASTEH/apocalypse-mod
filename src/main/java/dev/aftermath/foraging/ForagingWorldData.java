package dev.aftermath.foraging;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Per-dimension persistence for generated forage loot, depleted resource spots and recent rain.
 * The search service never creates hidden entities; this compact index is the persistent world state.
 */
public final class ForagingWorldData extends SavedData {
    private static final String DATA_NAME = "aftermath_foraging";
    private static final long REGROWTH_TICKS = 7L * 24_000L;
    private static final long RECENT_RAIN_TICKS = 12_000L;
    private static final int MAX_SPOTS = 8192;
    private static final Factory<ForagingWorldData> FACTORY =
            new Factory<>(ForagingWorldData::new, ForagingWorldData::load, null);

    private final Map<Long, Long> depletedAt = new HashMap<>();
    private final Map<Long, StoredFind> generatedFinds = new HashMap<>();
    private long lastRainAt = Long.MIN_VALUE;

    private ForagingWorldData() {}

    public static ForagingWorldData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public boolean isDepleted(BlockPos pos, long now) {
        long key = pos.asLong();
        Long collectedAt = depletedAt.get(key);
        if (collectedAt == null) return false;
        if (now >= collectedAt && now - collectedAt >= REGROWTH_TICKS) {
            depletedAt.remove(key);
            generatedFinds.remove(key);
            setDirty();
            return false;
        }
        return true;
    }

    public StoredFind findAt(BlockPos pos) {
        return generatedFinds.get(pos.asLong());
    }

    /** Remember the first generated result so changing focus or relogging cannot reroll the spot. */
    public StoredFind rememberFind(BlockPos pos, ResourceLocation itemId, int count, long now) {
        if (itemId == null || count < 1 || count > 64) return null;
        long key = pos.asLong();
        StoredFind existing = generatedFinds.get(key);
        if (existing != null) return existing;

        StoredFind stored = new StoredFind(itemId, count, Math.max(0L, now));
        generatedFinds.put(key, stored);
        trimOldest();
        setDirty();
        return stored;
    }

    public void markDepleted(BlockPos pos, long now) {
        long key = pos.asLong();
        depletedAt.put(key, Math.max(0L, now));
        generatedFinds.remove(key);
        trimOldest();
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

    private void trimOldest() {
        while (generatedFinds.size() + depletedAt.size() > MAX_SPOTS) {
            Long oldestFind = generatedFinds.entrySet().stream()
                    .min(Map.Entry.comparingByValue(java.util.Comparator.comparingLong(StoredFind::generatedAt)))
                    .map(Map.Entry::getKey).orElse(null);
            Long oldestDepleted = depletedAt.entrySet().stream()
                    .min(Map.Entry.comparingByValue(Long::compareTo))
                    .map(Map.Entry::getKey).orElse(null);
            if (oldestFind == null && oldestDepleted == null) break;
            if (oldestDepleted == null || (oldestFind != null
                    && generatedFinds.get(oldestFind).generatedAt() <= depletedAt.get(oldestDepleted))) {
                generatedFinds.remove(oldestFind);
            } else {
                depletedAt.remove(oldestDepleted);
            }
        }
    }

    private static ForagingWorldData load(CompoundTag tag, HolderLookup.Provider registries) {
        ForagingWorldData data = new ForagingWorldData();
        data.lastRainAt = tag.contains("last_rain_at", Tag.TAG_LONG)
                ? tag.getLong("last_rain_at") : Long.MIN_VALUE;

        ListTag depleted = tag.getList("depleted", Tag.TAG_COMPOUND);
        for (int i = 0; i < depleted.size() && i < MAX_SPOTS; i++) {
            CompoundTag entry = depleted.getCompound(i);
            if (entry.contains("pos", Tag.TAG_LONG) && entry.contains("time", Tag.TAG_LONG)) {
                data.depletedAt.put(entry.getLong("pos"), Math.max(0L, entry.getLong("time")));
            }
        }

        ListTag generated = tag.getList("generated", Tag.TAG_COMPOUND);
        for (int i = 0; i < generated.size() && i < MAX_SPOTS; i++) {
            CompoundTag entry = generated.getCompound(i);
            if (!entry.contains("pos", Tag.TAG_LONG) || !entry.contains("item", Tag.TAG_STRING)
                    || !entry.contains("count", Tag.TAG_INT)) continue;
            ResourceLocation itemId = ResourceLocation.tryParse(entry.getString("item"));
            int count = entry.getInt("count");
            long generatedAt = entry.contains("time", Tag.TAG_LONG) ? Math.max(0L, entry.getLong("time")) : 0L;
            if (itemId != null && count >= 1 && count <= 64) {
                long key = entry.getLong("pos");
                if (!data.depletedAt.containsKey(key)) {
                    data.generatedFinds.put(key, new StoredFind(itemId, count, generatedAt));
                }
            }
        }
        data.trimOldest();
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

        ListTag generated = new ListTag();
        generatedFinds.forEach((position, find) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("pos", position);
            entry.putString("item", find.itemId().toString());
            entry.putInt("count", find.count());
            entry.putLong("time", find.generatedAt());
            generated.add(entry);
        });
        tag.put("generated", generated);
        return tag;
    }

    public record StoredFind(ResourceLocation itemId, int count, long generatedAt) {}
}
