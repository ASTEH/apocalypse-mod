package dev.aftermath.character;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

/** Persistent weapon-class experience owned by the logical server. */
public record WeaponProgress(Map<String, Integer> experience, int schemaVersion) {
    private static final int CURRENT_SCHEMA_VERSION = 1;
    private static final int MAX_SAVED_XP = 2_000_000_000;

    /** Level thresholds used before the 600-kill progression rebalance. */
    private static final int[] LEGACY_LEVEL_THRESHOLDS = {
            0, 25, 50, 125, 250, 425, 650, 925, 1250, 1625, 2050
    };

    /** 10 XP per kill means level 10 is reached after approximately 600 eligible kills. */
    private static final int[] LEVEL_THRESHOLDS = {
            0, 300, 700, 1200, 1800, 2500, 3300, 4100, 4900, 5600, 6000
    };

    public static final Codec<WeaponProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT)
                    .optionalFieldOf("experience", Map.of())
                    .forGetter(WeaponProgress::experience),
            Codec.INT.optionalFieldOf("version", 0)
                    .forGetter(WeaponProgress::schemaVersion)
    ).apply(instance, WeaponProgress::fromSerialized));

    public WeaponProgress(Map<String, Integer> experience) {
        this(experience, CURRENT_SCHEMA_VERSION);
    }

    public WeaponProgress {
        Map<String, Integer> sanitized = new LinkedHashMap<>();
        if (experience != null) {
            experience.forEach((weaponClass, xp) -> {
                if (weaponClass != null && !weaponClass.isBlank() && xp != null && xp >= 0) {
                    sanitized.put(weaponClass, Math.min(xp, MAX_SAVED_XP));
                }
            });
        }
        experience = Map.copyOf(sanitized);
        schemaVersion = Math.max(CURRENT_SCHEMA_VERSION, schemaVersion);
    }

    public static WeaponProgress empty() {
        return new WeaponProgress(Map.of());
    }

    public int experienceFor(String weaponClass) {
        return experience.getOrDefault(weaponClass, 0);
    }

    public WeaponProgress addExperience(String weaponClass, int amount) {
        if (weaponClass == null || weaponClass.isBlank() || amount <= 0) return this;
        long sum = (long) experienceFor(weaponClass) + amount;
        int bounded = (int) Math.min(sum, MAX_SAVED_XP);
        Map<String, Integer> updated = new LinkedHashMap<>(experience);
        updated.put(weaponClass, bounded);
        return new WeaponProgress(updated);
    }

    public static int thresholdForLevel(int level) {
        return LEVEL_THRESHOLDS[Math.max(0, Math.min(10, level))];
    }

    private static WeaponProgress fromSerialized(Map<String, Integer> experience, int version) {
        Map<String, Integer> loaded = version < CURRENT_SCHEMA_VERSION
                ? migrateLegacyExperience(experience)
                : experience;
        return new WeaponProgress(loaded, CURRENT_SCHEMA_VERSION);
    }

    /**
     * Preserve the player's level and progress within that level when old saves are upgraded
     * from the 2050 XP cap to the new 6000 XP level-10 requirement.
     */
    private static Map<String, Integer> migrateLegacyExperience(Map<String, Integer> legacy) {
        Map<String, Integer> migrated = new LinkedHashMap<>();
        if (legacy == null) return migrated;

        legacy.forEach((weaponClass, oldXp) -> {
            if (weaponClass == null || weaponClass.isBlank() || oldXp == null || oldXp < 0) return;
            migrated.put(weaponClass, migrateLegacyXp(oldXp));
        });
        return migrated;
    }

    private static int migrateLegacyXp(int oldXp) {
        if (oldXp <= 0) return 0;

        int oldLevel = 0;
        for (int candidate = 1; candidate <= 10; candidate++) {
            if (oldXp >= LEGACY_LEVEL_THRESHOLDS[candidate]) {
                oldLevel = candidate;
            } else {
                break;
            }
        }

        if (oldLevel >= 10) {
            long overflow = (long) oldXp - LEGACY_LEVEL_THRESHOLDS[10];
            long mapped = LEVEL_THRESHOLDS[10]
                    + Math.round(overflow * (double) LEVEL_THRESHOLDS[10] / LEGACY_LEVEL_THRESHOLDS[10]);
            return (int) Math.min(mapped, MAX_SAVED_XP);
        }

        int oldStart = LEGACY_LEVEL_THRESHOLDS[oldLevel];
        int oldEnd = LEGACY_LEVEL_THRESHOLDS[oldLevel + 1];
        int newStart = LEVEL_THRESHOLDS[oldLevel];
        int newEnd = LEVEL_THRESHOLDS[oldLevel + 1];
        double progress = (oldXp - oldStart) / (double) (oldEnd - oldStart);
        int migrated = (int) Math.round(newStart + progress * (newEnd - newStart));
        return Math.max(0, Math.min(migrated, MAX_SAVED_XP));
    }
}
