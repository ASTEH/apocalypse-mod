package dev.aftermath.state;

import com.mojang.serialization.Codec;

import java.util.LinkedHashMap;
import java.util.Map;

/** Persistent, server-authoritative numeric values for player Moodles. */
public record PlayerStateData(Map<String, Double> values) {
    public static final Codec<PlayerStateData> CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).xmap(PlayerStateData::new, PlayerStateData::values);

    public PlayerStateData {
        Map<String, Double> safe = new LinkedHashMap<>();
        if (values != null) {
            values.forEach((id, value) -> {
                if (id != null && !id.isBlank() && value != null && Double.isFinite(value)) {
                    safe.put(id, Math.max(-1000.0D, Math.min(1000.0D, value)));
                }
            });
        }
        values = Map.copyOf(safe);
    }

    public static PlayerStateData empty() {
        return new PlayerStateData(Map.of());
    }

    public double get(String id, double fallback) {
        return values.getOrDefault(id, fallback);
    }

    public PlayerStateData withValue(String id, double value) {
        Map<String, Double> updated = new LinkedHashMap<>(values);
        updated.put(id, value);
        return new PlayerStateData(updated);
    }
}
