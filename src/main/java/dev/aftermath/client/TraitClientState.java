package dev.aftermath.client;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Holds selected trait IDs and server-derived firearm handling values for the local player. */
public final class TraitClientState {
    private static volatile Set<String> selectedTraits = Set.of();
    private static volatile float firearmSpreadMultiplier = 1.0F;
    private static volatile float firearmRecoilMultiplier = 1.0F;
    private static volatile float aimingSwayDegrees = 0.0F;
    private static volatile float quickReloadDurationMultiplier = 1.0F;

    public static void setSelectedTraits(String csv) {
        setSelectedTraits(csv, 1.0F, 1.0F, 0.0F, 1.0F);
    }

    public static void setSelectedTraits(String csv, float spreadMultiplier, float recoilMultiplier,
                                         float swayDegrees, float reloadMultiplier) {
        if (csv == null || csv.isBlank()) {
            selectedTraits = Set.of();
        } else {
            selectedTraits = Arrays.stream(csv.split(","))
                    .map(String::trim)
                    .filter(id -> !id.isEmpty())
                    .collect(Collectors.toUnmodifiableSet());
        }
        firearmSpreadMultiplier = sanitizeMultiplier(spreadMultiplier, 0.1F, 4.0F, 1.0F);
        firearmRecoilMultiplier = sanitizeMultiplier(recoilMultiplier, 0.1F, 4.0F, 1.0F);
        aimingSwayDegrees = Float.isFinite(swayDegrees) ? Math.max(0.0F, Math.min(3.0F, swayDegrees)) : 0.0F;
        quickReloadDurationMultiplier = sanitizeMultiplier(reloadMultiplier, 0.1F, 1.0F, 1.0F);
    }

    public static boolean has(String traitPath) {
        return selectedTraits.contains("aftermath:" + traitPath);
    }

    public static float firearmSpreadMultiplier() {
        return firearmSpreadMultiplier;
    }

    public static float firearmRecoilMultiplier() {
        return firearmRecoilMultiplier;
    }

    public static float aimingSwayDegrees() {
        return aimingSwayDegrees;
    }

    public static float quickReloadDurationMultiplier() {
        return quickReloadDurationMultiplier;
    }

    private static float sanitizeMultiplier(float value, float min, float max, float fallback) {
        if (!Float.isFinite(value)) return fallback;
        return Math.max(min, Math.min(max, value));
    }

    private TraitClientState() {}
}
