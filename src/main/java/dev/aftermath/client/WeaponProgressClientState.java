package dev.aftermath.client;

import dev.aftermath.character.WeaponProgressionService;
import dev.aftermath.network.WeaponProgressSyncPayload;

import java.util.HashMap;
import java.util.Map;

/** Read-only client snapshot of server-owned firearm progression. */
public final class WeaponProgressClientState {
    private static volatile Map<String, Integer> experience = Map.of();
    private static volatile float untrainedAimTimeMultiplier = 1.4F;
    private static volatile float untrainedRecoilPitch = 1.25F;
    private static volatile float untrainedRecoilYaw = 0.45F;

    public static void apply(WeaponProgressSyncPayload payload) {
        Map<String, Integer> parsed = new HashMap<>();
        if (payload.experienceCsv() != null && !payload.experienceCsv().isBlank()) {
            for (String entry : payload.experienceCsv().split(";")) {
                String[] pair = entry.split("=", 2);
                if (pair.length != 2) continue;
                try {
                    int xp = Integer.parseInt(pair[1]);
                    if (xp >= 0) parsed.put(pair[0], xp);
                } catch (NumberFormatException ignored) {
                    // Ignore malformed entries; the client cannot mutate the server snapshot.
                }
            }
        }
        experience = Map.copyOf(parsed);
        untrainedAimTimeMultiplier = Math.max(1.0F, payload.untrainedAimTimeMultiplier());
        untrainedRecoilPitch = Math.max(0.0F, payload.untrainedRecoilPitch());
        untrainedRecoilYaw = Math.max(0.0F, payload.untrainedRecoilYaw());
    }

    public static int levelFor(String weaponClass) {
        if (weaponClass == null) return 10;
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

    private WeaponProgressClientState() {}
}
