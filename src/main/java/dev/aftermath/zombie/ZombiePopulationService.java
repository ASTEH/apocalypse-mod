package dev.aftermath.zombie;

import dev.aftermath.config.AftermathServerConfig;
import dev.aftermath.noise.ZombieEntityClassifier;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

/**
 * Reduces zombie pressure during daylight without deleting existing mobs or blocking manual
 * spawn eggs, spawners, or structure-generated mobs. The chance is server-configurable.
 */
public final class ZombiePopulationService {
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (!AftermathServerConfig.ENABLE_DAYTIME_ZOMBIE_SUPPRESSION.get()) return;

        Mob mob = event.getEntity();
        if (!ZombieEntityClassifier.isZombieLike(mob) || !isDaytimeSuppressedSpawn(event.getSpawnType())) return;

        Level level = mob.level();
        if (level.isClientSide || !Level.OVERWORLD.equals(level.dimension()) || !level.isDay()) return;

        int allowedChance = AftermathServerConfig.DAYTIME_ZOMBIE_SPAWN_CHANCE_PERCENT.get();
        if (allowedChance > 0 && mob.getRandom().nextInt(100) < allowedChance) return;

        // In addition to cancelling the event, mark the mob so the NeoForge spawn pipeline
        // cannot add it to the world even when another mod wraps the spawn operation.
        mob.setSpawnCancelled(true);
        event.setCanceled(true);
    }

    private static boolean isDaytimeSuppressedSpawn(MobSpawnType type) {
        return type == MobSpawnType.NATURAL
                || type == MobSpawnType.REINFORCEMENT
                || type == MobSpawnType.CHUNK_GENERATION
                || type == MobSpawnType.EVENT
                || type == MobSpawnType.MOB_SUMMONED
                || type == MobSpawnType.TRIGGERED
                || type == MobSpawnType.PATROL;
    }

    private ZombiePopulationService() {}
}
