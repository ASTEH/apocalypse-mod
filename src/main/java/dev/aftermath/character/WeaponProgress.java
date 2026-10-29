package dev.aftermath.character;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

/** Persistent weapon-class experience owned by the logical server. */
public record WeaponProgress(Map<String, Integer> experience) {
    private static final int MAX_SAVED_XP = 2_000_000_000;

    public static final Codec<WeaponProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT)
                    .optionalFieldOf("experience", Map.of())
                    .forGetter(WeaponProgress::experience)
    ).apply(instance, WeaponProgress::new));

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
}
