package dev.aftermath.world;

import dev.aftermath.AftermathMod;
import dev.aftermath.config.AftermathServerConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Rarity filter for military NPCs from optional mods.
 *
 * TaCZ: Simple Enemy creates whole squads directly, finalizes them with MobSpawnType.EVENT,
 * then adds each entity to the level. The group decision is made on the first finalized unit
 * of a server tick and reused for the rest of that event's squad, so squads are not reduced
 * to random partial groups. Rejected entities are stopped at EntityJoinLevelEvent.
 */
public final class MilitarySpawnControlService {
    private static final Set<UUID> REJECTED_EVENT_SPAWNS = new HashSet<>();
    private static final Map<ServerLevel, GroupDecision> EVENT_GROUP_DECISIONS = new WeakHashMap<>();

    private MilitarySpawnControlService() {}

    /**
     * Fallback for spawn systems that use Minecraft's normal position-check pipeline.
     * EVENT spawns are handled as complete groups in onFinalizeSpawn instead.
     */
    public static void onMobPositionCheck(MobSpawnEvent.PositionCheck event) {
        if (!AftermathServerConfig.ENABLE_MILITARY_SPAWN_CONTROL.get()) return;
        if (event.getSpawnType() == MobSpawnType.EVENT) return;

        Mob mob = event.getEntity();
        if (!(mob.level() instanceof ServerLevel level)) return;

        MilitarySpawnDefinitions.Snapshot policy = MilitarySpawnDefinitions.snapshot();
        if (!policy.shouldLimit(mob, level.dimension().location(), event.getSpawnType().name())) return;

        double chance = policy.spawnChance(level.dimension().location(), mob.blockPosition());
        if (chance <= 0.0D || (chance < 1.0D && mob.getRandom().nextDouble() >= chance)) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }

    /**
     * Runs for Simple Enemy's manual group spawning path, which uses MobSpawnType.EVENT.
     * The decision is cached by level and game tick so every unit in the same squad survives
     * or is rejected together. Spawn eggs and command summons use different spawn types.
     */
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (!AftermathServerConfig.ENABLE_MILITARY_SPAWN_CONTROL.get() || event.isCanceled()) return;
        if (event.getSpawnType() != MobSpawnType.EVENT) return;

        Mob mob = event.getEntity();
        if (!(mob.level() instanceof ServerLevel level)) return;

        MilitarySpawnDefinitions.Snapshot policy = MilitarySpawnDefinitions.snapshot();
        if (!policy.shouldLimit(mob, level.dimension().location(), "event")) return;

        long gameTime = level.getGameTime();
        GroupDecision decision = EVENT_GROUP_DECISIONS.get(level);
        if (decision == null || decision.gameTime() != gameTime) {
            double chance = policy.spawnChance(level.dimension().location(), mob.blockPosition());
            boolean allow = chance >= 1.0D || (chance > 0.0D && mob.getRandom().nextDouble() < chance);
            decision = new GroupDecision(gameTime, allow);
            EVENT_GROUP_DECISIONS.put(level, decision);
            AftermathMod.LOGGER.debug(
                    "Military NPC event group {} in {} at {}: {} (chance={}).",
                    mob.getType(), level.dimension().location(), mob.blockPosition(),
                    allow ? "allowed" : "rejected", chance);
        }

        if (!decision.allowed()) {
            REJECTED_EVENT_SPAWNS.add(mob.getUUID());
        }
    }

    /** Prevents an event-rejected soldier from being inserted into the world. */
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (REJECTED_EVENT_SPAWNS.remove(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }

    public static void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        REJECTED_EVENT_SPAWNS.clear();
        EVENT_GROUP_DECISIONS.clear();
    }

    private record GroupDecision(long gameTime, boolean allowed) {}
}
