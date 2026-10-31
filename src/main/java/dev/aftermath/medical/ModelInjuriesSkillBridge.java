package dev.aftermath.medical;

import dev.aftermath.AftermathMod;
import dev.aftermath.learning.LearningService;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Server-authoritative XP bridge for successful Model Injuries treatment mutations. */
public final class ModelInjuriesSkillBridge {
    private static final ThreadLocal<ArrayDeque<ServerPlayer>> ACTIVE_PLAYERS =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static final Set<String> ANNOUNCED_HOOKS = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger COMPLETED_PROCEDURES = new AtomicInteger();
    private static final AtomicInteger XP_AWARDED_PROCEDURES = new AtomicInteger();

    private ModelInjuriesSkillBridge() {}

    public static void begin(Object player) {
        if (player instanceof ServerPlayer serverPlayer && !serverPlayer.level().isClientSide()) {
            ACTIVE_PLAYERS.get().push(serverPlayer);
        } else {
            ACTIVE_PLAYERS.remove();
        }
    }

    public static void end() {
        ArrayDeque<ServerPlayer> players = ACTIVE_PLAYERS.get();
        if (!players.isEmpty()) players.pop();
        if (players.isEmpty()) ACTIVE_PLAYERS.remove();
    }

    /** Emits one diagnostic per path when its optional Mixin is actually invoked. */
    public static void hookEntered(String path) {
        if (ANNOUNCED_HOOKS.add(path)) {
            AftermathMod.LOGGER.info(
                    "Model Injuries {} hook is attached; waiting for a successful treatment or medicine action.",
                    path);
        }
    }

    /** Called only when InjuryState.treat(...) or InjuryState.takeMedicine(...) returns true. */
    public static void onSuccessfulProcedure() {
        ServerPlayer player = ACTIVE_PLAYERS.get().peek();
        if (player == null || !player.isAlive()) return;

        int completed = COMPLETED_PROCEDURES.incrementAndGet();
        boolean awarded = LearningService.awardFirstAidXp(player);
        if (awarded) XP_AWARDED_PROCEDURES.incrementAndGet();

        if (completed == 1) {
            AftermathMod.LOGGER.info(
                    "Model Injuries treatment bridge observed its first successful procedure for {}. XP awarded: {}.",
                    player.getGameProfile().getName(), awarded);
        } else {
            AftermathMod.LOGGER.debug(
                    "Model Injuries procedure completed for {}. XP awarded: {}; procedures observed this session: {}.",
                    player.getGameProfile().getName(), awarded, completed);
        }
    }

    public static int completedProceduresThisSession() {
        return COMPLETED_PROCEDURES.get();
    }

    public static int xpAwardedProceduresThisSession() {
        return XP_AWARDED_PROCEDURES.get();
    }
}
