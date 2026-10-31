package dev.aftermath.medical;

import dev.aftermath.AftermathMod;
import dev.aftermath.learning.LearningService;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Server-side bridge from First Aid New's public treatment callback into Aftermath skill XP.
 * The callback object is weakly deduplicated because both the public API hook and the
 * DamageablePart fallback can observe the same treatment.
 */
public final class FirstAidSkillBridge {
    private static final Set<Object> SEEN_CONTEXTS =
            Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<>()));
    private static final AtomicInteger COMPLETED_TREATMENTS = new AtomicInteger();
    private static final AtomicInteger AWARDED_TREATMENTS = new AtomicInteger();
    private static boolean warnedAboutContext;

    private FirstAidSkillBridge() {}

    public static void onTreatmentCompleted(Object context) {
        if (context == null) return;
        synchronized (SEEN_CONTEXTS) {
            if (!SEEN_CONTEXTS.add(context)) return;
        }

        try {
            Method getPlayer = context.getClass().getMethod("getPlayer");
            Object rawPlayer = getPlayer.invoke(context);
            if (!(rawPlayer instanceof ServerPlayer player) || player.level().isClientSide()) return;

            int count = COMPLETED_TREATMENTS.incrementAndGet();
            boolean awarded = LearningService.awardFirstAidXp(player);
            if (awarded) AWARDED_TREATMENTS.incrementAndGet();
            if (count == 1) {
                AftermathMod.LOGGER.info(
                        "First Aid New treatment callback is active. Completed server-side treatments observed: 1; XP awarded: {}.",
                        awarded);
            } else {
                AftermathMod.LOGGER.debug(
                        "First Aid New treatment completed for {}. XP awarded: {}; callback count this session: {}.",
                        player.getGameProfile().getName(), awarded, count);
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (!warnedAboutContext) {
                warnedAboutContext = true;
                AftermathMod.LOGGER.warn(
                        "First Aid New treatment callback ran, but Aftermath could not read its player context. "
                                + "First Aid XP will not be awarded until this compatibility issue is fixed.",
                        exception);
            }
        }
    }

    public static int completedTreatmentsThisSession() {
        return COMPLETED_TREATMENTS.get();
    }

    public static int awardedTreatmentsThisSession() {
        return AWARDED_TREATMENTS.get();
    }
}
