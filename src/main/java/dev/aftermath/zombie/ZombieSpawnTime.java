package dev.aftermath.zombie;

import net.minecraft.world.level.Level;

/** Shared vanilla day/night boundaries used by zombie spawn rules and archetype selection. */
public final class ZombieSpawnTime {
    public static final long DAY_LENGTH_TICKS = 24_000L;
    public static final long NIGHT_START_TICK = 13_000L;
    public static final long NIGHT_END_TICK = 23_000L;

    private ZombieSpawnTime() {}

    /**
     * Vanilla night interval: dusk at tick 13000 through tick 22999.
     * The remaining time (23000 through 12999) is treated as daytime.
     */
    public static boolean isNight(Level level) {
        long timeOfDay = Math.floorMod(level.getDayTime(), DAY_LENGTH_TICKS);
        return timeOfDay >= NIGHT_START_TICK && timeOfDay < NIGHT_END_TICK;
    }
}
