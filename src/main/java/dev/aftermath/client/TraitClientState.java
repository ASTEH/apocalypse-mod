package dev.aftermath.client;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Holds selected trait IDs and server-derived effective trait strengths for the local player. */
public final class TraitClientState {
    private static volatile Set<String> selectedTraits = Set.of();
    private static volatile float eagleEyeSpreadMultiplier = 1.0F;
    private static volatile float quickReloadDurationMultiplier = 1.0F;

    public static void setSelectedTraits(String csv) {
        setSelectedTraits(csv, 1.0F, 1.0F);
    }

    public static void setSelectedTraits(String csv, float eagleEyeMultiplier, float quickReloadMultiplier) {
        if (csv == null || csv.isBlank()) {
            selectedTraits = Set.of();
        } else {
            selectedTraits = Arrays.stream(csv.split(","))
                    .map(String::trim)
                    .filter(id -> !id.isEmpty())
                    .collect(Collectors.toUnmodifiableSet());
        }
        eagleEyeSpreadMultiplier = sanitizeMultiplier(eagleEyeMultiplier);
        quickReloadDurationMultiplier = sanitizeMultiplier(quickReloadMultiplier);
    }

    public static boolean has(String traitPath) {
        return selectedTraits.contains("aftermath:" + traitPath);
    }

    public static float eagleEyeSpreadMultiplier() {
        return eagleEyeSpreadMultiplier;
    }

    public static float quickReloadDurationMultiplier() {
        return quickReloadDurationMultiplier;
    }

    private static float sanitizeMultiplier(float value) {
        if (!Float.isFinite(value)) return 1.0F;
        return Math.max(0.1F, Math.min(1.0F, value));
    }

    private TraitClientState() {}
}
