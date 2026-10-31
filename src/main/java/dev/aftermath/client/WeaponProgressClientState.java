package dev.aftermath.client;

import dev.aftermath.character.WeaponProgressionService;
import dev.aftermath.network.WeaponProgressSyncPayload;

import java.util.HashMap;
import java.util.Map;

/** Read-only client snapshot of server-owned firearm progression. */
public final class WeaponProgressClientState {
    private static volatile Map<String, Integer> experience = Map.of();
    private static volatile boolean progressionEnabled = true;
    private static volatile float untrainedAimTimeMultiplier = 1.65F;
    private static volatile float untrainedRecoilPitch = 2.0F;
    private static volatile float untrainedRecoilYaw = 0.8F;
    private static volatile float untrainedShotgunHandlingMultiplier = 1.25F;
    private static volatile float untrainedHeavyHandlingMultiplier = 1.4F;
    private static volatile float untrainedSniperHandlingMultiplier = 1.2F;

    public static void apply(WeaponProgressSyncPayload payload) {
        Map<String, Integer> parsedExperience = new HashMap<>();
        parsePairs(payload.experienceCsv()).forEach((weaponClass, value) -> {
            try {
                int xp = Integer.parseInt(value);
                if (xp >= 0) parsedExperience.put(weaponClass, xp);
            } catch (NumberFormatException ignored) {
                // Ignore malformed entries; the client cannot mutate the server snapshot.
            }
        });

        Map<String, Float> parsedMultipliers = new HashMap<>();
        parsePairs(payload.handlingMultipliersCsv()).forEach((weaponClass, value) -> {
            try {
                float multiplier = Float.parseFloat(value);
                if (Float.isFinite(multiplier)) parsedMultipliers.put(weaponClass, multiplier);
            } catch (NumberFormatException ignored) {
                // Retain safe defaults when an optional class multiplier is malformed.
            }
        });

        experience = Map.copyOf(parsedExperience);
        progressionEnabled = payload.progressionEnabled();
        untrainedAimTimeMultiplier = Math.max(1.0F, payload.untrainedAimTimeMultiplier());
        untrainedRecoilPitch = Math.max(0.0F, payload.untrainedRecoilPitch());
        untrainedRecoilYaw = Math.max(0.0F, payload.untrainedRecoilYaw());
        untrainedShotgunHandlingMultiplier = clampHandlingMultiplier(
                parsedMultipliers.getOrDefault("shotgun", 1.25F));
        untrainedHeavyHandlingMultiplier = clampHandlingMultiplier(
                parsedMultipliers.getOrDefault("heavy", 1.4F));
        untrainedSniperHandlingMultiplier = clampHandlingMultiplier(
                parsedMultipliers.getOrDefault("sniper", 1.2F));
    }

    private static Map<String, String> parsePairs(String csv) {
        Map<String, String> parsed = new HashMap<>();
        if (csv == null || csv.isBlank()) return parsed;
        for (String entry : csv.split(";")) {
            String[] pair = entry.split("=", 2);
            if (pair.length == 2 && !pair[0].isBlank() && !pair[1].isBlank()) {
                parsed.put(pair[0], pair[1]);
            }
        }
        return parsed;
    }

    public static int levelFor(String weaponClass) {
        if (!progressionEnabled || weaponClass == null) return 10;
        return WeaponProgressionService.levelFor(experience.getOrDefault(weaponClass, 0));
    }

    public static float untrainedAimTimeMultiplier() {
        return untrainedAimTimeMultiplier;
    }

    public static float untrainedRecoilPitch() {
        return untrainedRecoilPitch;
    }

    public static float untrainedRecoilYaw() {
        return untrainedRecoilYaw;
    }

    public static float handlingClassMultiplier(String weaponClass) {
        if ("shotgun".equals(weaponClass)) return untrainedShotgunHandlingMultiplier;
        if ("heavy".equals(weaponClass)) return untrainedHeavyHandlingMultiplier;
        if ("sniper".equals(weaponClass)) return untrainedSniperHandlingMultiplier;
        return 1.0F;
    }

    private static float clampHandlingMultiplier(float multiplier) {
        return Math.max(1.0F, Math.min(3.0F, multiplier));
    }

    private WeaponProgressClientState() {}
}
