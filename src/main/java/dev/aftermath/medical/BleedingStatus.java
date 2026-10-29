package dev.aftermath.medical;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Persisted wound state. The countdown advances only while the player is ticking on the server.
 * Severity is not copied on death, so every respawn begins without the previous life's wounds.
 */
public record BleedingStatus(int severity, int ticksUntilDamage) {
    public static final Codec<BleedingStatus> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("severity", 0).forGetter(BleedingStatus::severity),
            Codec.INT.optionalFieldOf("ticks_until_damage", 0).forGetter(BleedingStatus::ticksUntilDamage)
    ).apply(instance, BleedingStatus::new));

    public BleedingStatus {
        severity = Math.max(0, Math.min(100, severity));
        ticksUntilDamage = severity == 0 ? 0 : Math.max(0, Math.min(72_000, ticksUntilDamage));
    }

    public static BleedingStatus empty() {
        return new BleedingStatus(0, 0);
    }

    public boolean isBleeding() {
        return severity > 0;
    }
}
