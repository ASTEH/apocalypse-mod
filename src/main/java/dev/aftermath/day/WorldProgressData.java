package dev.aftermath.day;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * World-wide apocalypse day counter. Data is stored only on the Overworld so all dimensions
 * share one timeline. It follows server game ticks, not client time or /time set commands.
 */
public final class WorldProgressData extends SavedData {
    private static final String DATA_NAME = "aftermath_world_progress";
    private static final long DAY_TICKS = 24_000L;
    private static final int MAX_DAY = 36_500;

    private static final Factory<WorldProgressData> FACTORY =
            new Factory<>(WorldProgressData::new, WorldProgressData::load, null);

    private long startGameTime;
    private int highestDay = 1;
    private boolean initialized;

    private WorldProgressData() {}

    public static int currentDay(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        WorldProgressData data = overworld.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
        return data.observe(overworld);
    }

    public static int setDay(MinecraftServer server, int requestedDay) {
        int boundedDay = Math.max(1, Math.min(MAX_DAY, requestedDay));
        ServerLevel overworld = server.overworld();
        WorldProgressData data = overworld.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
        data.startGameTime = overworld.getGameTime() - (long) (boundedDay - 1) * DAY_TICKS;
        data.highestDay = boundedDay;
        data.initialized = true;
        data.setDirty();
        return data.observe(overworld);
    }

    private int observe(ServerLevel overworld) {
        long now = overworld.getGameTime();
        if (!initialized) {
            long seedDayIndex = Math.max(0L, overworld.getDayTime()) / DAY_TICKS;
            startGameTime = now - seedDayIndex * DAY_TICKS;
            highestDay = (int) Math.min(MAX_DAY, seedDayIndex + 1L);
            initialized = true;
            setDirty();
        }

        long elapsed = Math.max(0L, now - startGameTime);
        int calculatedDay = (int) Math.min(MAX_DAY, elapsed / DAY_TICKS + 1L);
        if (calculatedDay > highestDay) {
            highestDay = calculatedDay;
            setDirty();
        }
        return Math.max(1, highestDay);
    }

    private static WorldProgressData load(CompoundTag tag, HolderLookup.Provider registries) {
        WorldProgressData data = new WorldProgressData();
        data.startGameTime = tag.getLong("start_game_time");
        data.highestDay = Math.max(1, Math.min(MAX_DAY, tag.getInt("highest_day")));
        data.initialized = tag.getBoolean("initialized");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("start_game_time", startGameTime);
        tag.putInt("highest_day", highestDay);
        tag.putBoolean("initialized", initialized);
        return tag;
    }
}
