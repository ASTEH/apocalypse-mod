package dev.aftermath.medical;

import dev.aftermath.AftermathMod;
import dev.aftermath.learning.LearningService;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Awards Aftermath First Aid XP only when Model Injuries confirms that a treatment or
 * medicine application actually succeeded. The active player is scoped to a single
 * Model Injuries completion call and cleared on every normal return.
 */
public final class ModelInjuriesSkillBridge {
    private static final ThreadLocal<ServerPlayer> ACTIVE_PLAYER = new ThreadLocal<>();
    private static final AtomicInteger COMPLETED_PROCEDURES = new AtomicInteger();
    private static final AtomicInteger XP_AWARDED_PROCEDURES = new AtomicInteger();

    private ModelInjuriesSkillBridge() {}

    public static void begin(Object player) {
        if (player instanceof ServerPlayer serverPlayer && !serverPlayer.level().isClientSide()) {
            ACTIVE_PLAYER.set(serverPlayer);
        } else {
            ACTIVE_PLAYER.remove();
        }
    }

    /** Called only when InjuryState.treat(...) or InjuryState.takeMedicine(...) returns true. */
    public static void onSuccessfulProcedure() {
        ServerPlayer player = ACTIVE_PLAYER.get();
        if (player == null || !player.isAlive()) return;

        int completed = COMPLETED_PROCEDURES.incrementAndGet();
        boolean awarded = LearningService.awardFirstAidXp(player);
        if (awarded) XP_AWARDED_PROCEDURES.incrementAndGet();

        if (completed == 1) {
            AftermathMod.LOGGER.info(
                    "Model Injuries treatment bridge is active. Successful procedures observed: 1; XP awarded: {}.",
                    awarded);
        } else {
            AftermathMod.LOGGER.debug(
                    "Model Injuries procedure completed for {}. XP awarded: {}; procedures observed this session: {}.",
                    player.getGameProfile().getName(), awarded, completed);
        }
    }

    public static void end() {
        ACTIVE_PLAYER.remove();
    }

    public static int completedProceduresThisSession() {
        return COMPLETED_PROCEDURES.get();
    }

    public static int xpAwardedProceduresThisSession() {
        return XP_AWARDED_PROCEDURES.get();
    }
}
