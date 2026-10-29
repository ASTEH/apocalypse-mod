package dev.aftermath.noise;
import dev.aftermath.config.AftermathServerConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Transient horde state; adds a goal without replacing any vanilla or third-party AI goals. */
public final class HordeDirector {
    private static final Map<UUID, HordeOrder> ORDERS = new HashMap<>();
    private static final Map<ResourceLocation, PathBudget> PATH_BUDGETS = new HashMap<>();
    private static final Map<Mob, Boolean> INSTALLED = new WeakHashMap<>();
    /** Optional in-memory administrator override; null means use the server config value. */
    private static volatile Boolean runtimeBehaviorOverride;

    public static boolean isHordeBehaviorEnabled() {
        Boolean override = runtimeBehaviorOverride;
        return override != null ? override : AftermathServerConfig.ENABLE_HORDE_BEHAVIOR.get();
    }

    public static boolean hasRuntimeBehaviorOverride() {
        return runtimeBehaviorOverride != null;
    }

    public static void setRuntimeBehaviorOverride(boolean enabled) {
        runtimeBehaviorOverride = enabled;
    }

    public static void resetRuntimeBehaviorOverride() {
        runtimeBehaviorOverride = null;
    }
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel) || !(event.getEntity() instanceof Mob mob)
                || !ZombieEntityClassifier.isZombieLikeType(mob)) return;
        if (INSTALLED.putIfAbsent(mob, Boolean.TRUE) == null) mob.goalSelector.addGoal(5, new BehaviorGoal(mob));
    }
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) { ORDERS.remove(event.getEntity().getUUID()); }

    public static AssignmentResult assignNoise(ServerLevel level, Vec3 target, List<Mob> mobs, int duration,
                                  ResourceLocation signalId, UUID sourceId,
                                  boolean grouping, int stageGroupLimit,
                                  NoiseDefinitions.HordeArchetypeDefinition archetype) {
        if (mobs.isEmpty() || signalId == null) return new AssignmentResult(0, 0);
        int priority = signalPriorityForDiagnostics(signalId);
        List<Mob> receptive = new java.util.ArrayList<>(mobs.size());
        for (Mob mob : mobs) {
            HordeOrder current = activeOrder(mob);
            if (shouldAcceptSignal(current, signalId, sourceId, priority)) receptive.add(mob);
        }
        // A weaker stimulus must not steal a zombie from an active, higher-priority investigation.
        if (receptive.isEmpty()) return new AssignmentResult(0, 0);

        Mob leader = null;
        if (grouping && archetype != null && archetype.leader()) {
            for (Mob mob : receptive) {
                HordeOrder old = activeOrder(mob);
                if (old != null && old.role() == Role.LEADER) { leader = mob; break; }
            }
            if (leader == null) leader = receptive.get(0);
        }
        long expires = level.getGameTime() + Math.max(1, duration);
        ResourceLocation dim = level.dimension().location();
        for (Mob mob : receptive) ORDERS.put(mob.getUUID(),
                new HordeOrder(dim, Role.INVESTIGATE, null, target, expires, 1.0D,
                        signalId, sourceId, priority));
        if (leader == null) return new AssignmentResult(receptive.size(), 0);

        int maxGroup = Math.min(AftermathServerConfig.MAX_HORDE_SIZE.get(),
                Math.min(Math.max(1, stageGroupLimit), archetype.maxFollowers() + 1));
        ORDERS.put(leader.getUUID(), new HordeOrder(dim, Role.LEADER, leader.getUUID(),
                target, expires, archetype.speedMultiplier(), signalId, sourceId, priority));
        double radiusSqr = (double) archetype.followerRadius() * archetype.followerRadius();
        int followers = 0;
        for (Mob mob : receptive) {
            if (mob == leader || followers >= maxGroup - 1 || mob.distanceToSqr(leader) > radiusSqr) continue;
            ORDERS.put(mob.getUUID(), new HordeOrder(dim, Role.FOLLOWER, leader.getUUID(),
                    target, expires, archetype.speedMultiplier(), signalId, sourceId, priority));
            followers++;
        }
        return new AssignmentResult(receptive.size(), followers);
    }

    public record AssignmentResult(int acceptedTargets, int groupedFollowers) {}

    /**
     * Priorities are intentionally categorical: blood is persistent and must not be displaced by
     * incidental lights or footsteps. A genuine gunshot can still supersede non-blood objectives.
     */
    static int signalPriorityForDiagnostics(ResourceLocation signalId) {
        String path = signalId.getPath();
        if (path.equals("blood_scent")) return 100;
        if (path.equals("gunshot")) return 90;
        if (path.equals("sound_loud") || path.equals("clumsy_sprint")) return 80;
        if (path.equals("sprint_footsteps")) return 75;
        if (path.equals("light_source")) return 60;
        if (path.equals("chat")) return 50;
        if (path.equals("block_action")) return 45;
        return 40;
    }

    private static boolean shouldAcceptSignal(HordeOrder current, ResourceLocation signalId,
                                              UUID sourceId, int priority) {
        if (current == null) return true;
        if (priority > current.priority()) return true;
        if (priority < current.priority()) return false;

        // Blood spots form a trail: an equal-priority blood scent may advance the pursuit to a
        // newer nearby spot, while other sources cannot make the zombie oscillate between targets.
        if (signalId.getPath().equals("blood_scent")
                && current.signalId().getPath().equals("blood_scent")) return true;
        return current.signalId().equals(signalId)
                && java.util.Objects.equals(current.sourceId(), sourceId);
    }

    public static HordeOrder activeOrder(Mob mob) {
        if (!AftermathServerConfig.ENABLE_NOISE_EVENTS.get() || mob.isRemoved()) return null;
        HordeOrder order = ORDERS.get(mob.getUUID());
        if (order == null) return null;
        if (!(mob.level() instanceof ServerLevel level) || !order.dimension().equals(level.dimension().location())
                || level.getGameTime() >= order.expiresAt()) {
            ORDERS.remove(mob.getUUID(), order);
            return null;
        }
        return order;
    }
    public static boolean consumePathRequest(ServerLevel level) {
        ResourceLocation dim = level.dimension().location();
        long tick = level.getGameTime();
        int cap = AftermathServerConfig.MAX_PATH_REQUESTS_PER_TICK.get();
        PathBudget state = PATH_BUDGETS.get(dim);
        if (state == null || state.tick() != tick) state = new PathBudget(tick, cap);
        if (state.remaining() <= 0) { PATH_BUDGETS.put(dim, state); return false; }
        PATH_BUDGETS.put(dim, new PathBudget(tick, state.remaining() - 1));
        return true;
    }
    public static void cleanupLevel(ServerLevel level) {
        ResourceLocation dim = level.dimension().location();
        long now = level.getGameTime();
        ORDERS.entrySet().removeIf(e -> e.getValue().dimension().equals(dim) && now >= e.getValue().expiresAt());
    }
    public static void onServerStopping(ServerStoppingEvent event) {
        ORDERS.clear(); PATH_BUDGETS.clear(); INSTALLED.clear();
        runtimeBehaviorOverride = null;
    }
    public enum Role { INVESTIGATE, LEADER, FOLLOWER }
    public record HordeOrder(ResourceLocation dimension, Role role, UUID leaderId, Vec3 targetPosition,
                             long expiresAt, double speedMultiplier, ResourceLocation signalId,
                             UUID sourceId, int priority) {}
    private record PathBudget(long tick, int remaining) {}

    private static final class BehaviorGoal extends Goal {
        private static final double ARRIVAL_SQR = 1.0D;
        private static final double FOLLOWER_ARRIVAL_SQR = 2.25D;
        private final Mob mob;
        private long nextPathUpdate;
        private BehaviorGoal(Mob mob) { this.mob = mob; setFlags(EnumSet.of(Goal.Flag.MOVE)); }
        @Override public boolean canUse() { return activeOrder(mob) != null; }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public void start() {
            if (mob.level() instanceof ServerLevel level) {
                int interval = Math.max(5, AftermathServerConfig.AI_RECALC_INTERVAL_TICKS.get());
                // Spread first path requests across the interval. Otherwise every zombie assigned
                // by one scent pulse asks for a path on the same tick and the per-tick budget can
                // starve the same mobs repeatedly.
                nextPathUpdate = level.getGameTime()
                        + Math.floorMod(mob.getUUID().hashCode(), interval);
            } else {
                nextPathUpdate = 0L;
            }
        }
        @Override public void tick() {
            if (!(mob.level() instanceof ServerLevel level)) return;
            HordeOrder order = activeOrder(mob);
            if (order == null) return;
            long now = level.getGameTime();
            if (now < nextPathUpdate) return;
            long interval = Math.max(5, AftermathServerConfig.AI_RECALC_INTERVAL_TICKS.get());
            if (order.role() == Role.FOLLOWER && isHordeBehaviorEnabled()
                    && order.leaderId() != null) {
                Entity e = level.getEntity(order.leaderId());
                if (e instanceof Mob leader && leader.isAlive() && !leader.isRemoved()) {
                    if (mob.distanceToSqr(leader) > FOLLOWER_ARRIVAL_SQR) {
                        if (consumePathRequest(level)) {
                            mob.getNavigation().moveTo(leader, Math.max(0.1D, order.speedMultiplier()));
                            nextPathUpdate = now + interval;
                        } else {
                            nextPathUpdate = retrySoon(now);
                        }
                    } else {
                        nextPathUpdate = now + interval;
                        if (!mob.getNavigation().isDone()) mob.getNavigation().stop();
                    }
                    return;
                }
            }
            Vec3 target = order.targetPosition();
            if (mob.position().distanceToSqr(target) > ARRIVAL_SQR) {
                if (consumePathRequest(level)) {
                    mob.getNavigation().moveTo(target.x, target.y, target.z,
                            Math.max(0.1D, order.speedMultiplier()));
                    nextPathUpdate = now + interval;
                } else {
                    // A denied budget request is retried quickly rather than after the full AI
                    // interval, preventing a group from getting permanently stuck on the same
                    // unlucky update cadence.
                    nextPathUpdate = retrySoon(now);
                }
            } else {
                nextPathUpdate = now + interval;
                if (!mob.getNavigation().isDone()) mob.getNavigation().stop();
            }
        }
        private long retrySoon(long now) {
            return now + 1L + Math.floorMod(mob.getUUID().hashCode() ^ (int) now, 5);
        }
        @Override public void stop() { if (!mob.getNavigation().isDone()) mob.getNavigation().stop(); }
    }
    private HordeDirector() {}
}
