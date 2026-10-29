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
                || !ZombieEntityClassifier.isZombieLike(mob)) return;
        if (INSTALLED.putIfAbsent(mob, Boolean.TRUE) == null) mob.goalSelector.addGoal(5, new BehaviorGoal(mob));
    }
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) { ORDERS.remove(event.getEntity().getUUID()); }

    public static int assignNoise(ServerLevel level, Vec3 target, List<Mob> mobs, int duration,
                                  boolean grouping, int stageGroupLimit,
                                  NoiseDefinitions.HordeArchetypeDefinition archetype) {
        if (mobs.isEmpty()) return 0;
        Mob leader = null;
        if (grouping && archetype != null && archetype.leader()) {
            for (Mob mob : mobs) {
                HordeOrder old = activeOrder(mob);
                if (old != null && old.role() == Role.LEADER) { leader = mob; break; }
            }
            if (leader == null) leader = mobs.get(0);
        }
        long expires = level.getGameTime() + Math.max(1, duration);
        ResourceLocation dim = level.dimension().location();
        for (Mob mob : mobs) ORDERS.put(mob.getUUID(),
                new HordeOrder(dim, Role.INVESTIGATE, null, target, expires, 1.0D));
        if (leader == null) return 0;

        int maxGroup = Math.min(AftermathServerConfig.MAX_HORDE_SIZE.get(),
                Math.min(Math.max(1, stageGroupLimit), archetype.maxFollowers() + 1));
        ORDERS.put(leader.getUUID(), new HordeOrder(dim, Role.LEADER, leader.getUUID(),
                target, expires, archetype.speedMultiplier()));
        double radiusSqr = (double) archetype.followerRadius() * archetype.followerRadius();
        int followers = 0;
        for (Mob mob : mobs) {
            if (mob == leader || followers >= maxGroup - 1 || mob.distanceToSqr(leader) > radiusSqr) continue;
            ORDERS.put(mob.getUUID(), new HordeOrder(dim, Role.FOLLOWER, leader.getUUID(),
                    target, expires, archetype.speedMultiplier()));
            followers++;
        }
        return followers;
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
                             long expiresAt, double speedMultiplier) {}
    private record PathBudget(long tick, int remaining) {}

    private static final class BehaviorGoal extends Goal {
        private static final double ARRIVAL_SQR = 9.0D;
        private final Mob mob;
        private long nextPathUpdate;
        private BehaviorGoal(Mob mob) { this.mob = mob; setFlags(EnumSet.of(Goal.Flag.MOVE)); }
        @Override public boolean canUse() { return activeOrder(mob) != null; }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public void start() { nextPathUpdate = 0L; }
        @Override public void tick() {
            if (!(mob.level() instanceof ServerLevel level)) return;
            HordeOrder order = activeOrder(mob);
            if (order == null) return;
            long now = level.getGameTime();
            if (now < nextPathUpdate) return;
            nextPathUpdate = now + Math.max(5, AftermathServerConfig.AI_RECALC_INTERVAL_TICKS.get());
            if (order.role() == Role.FOLLOWER && isHordeBehaviorEnabled()
                    && order.leaderId() != null) {
                Entity e = level.getEntity(order.leaderId());
                if (e instanceof Mob leader && leader.isAlive() && !leader.isRemoved()) {
                    if (mob.distanceToSqr(leader) > ARRIVAL_SQR) {
                        if (consumePathRequest(level)) mob.getNavigation().moveTo(leader, Math.max(0.1D, order.speedMultiplier()));
                    } else if (!mob.getNavigation().isDone()) mob.getNavigation().stop();
                    return;
                }
            }
            Vec3 target = order.targetPosition();
            if (mob.position().distanceToSqr(target) > ARRIVAL_SQR) {
                if (consumePathRequest(level)) mob.getNavigation().moveTo(target.x, target.y, target.z,
                        Math.max(0.1D, order.speedMultiplier()));
            } else if (!mob.getNavigation().isDone()) mob.getNavigation().stop();
        }
        @Override public void stop() { if (!mob.getNavigation().isDone()) mob.getNavigation().stop(); }
    }
    private HordeDirector() {}
}
