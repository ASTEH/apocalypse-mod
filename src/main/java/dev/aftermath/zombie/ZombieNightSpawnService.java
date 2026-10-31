package dev.aftermath.zombie;

import dev.aftermath.AftermathMod;
import dev.aftermath.config.AftermathServerConfig;
import dev.aftermath.noise.ZombieEntityClassifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

import java.util.EnumSet;
import java.util.Set;

/**
 * Allows zombie-like entities to enter the Overworld only during the normal Minecraft night.
 * Manual/admin creation and transformations are intentionally excluded.
 */
public final class ZombieNightSpawnService {
    private static final Set<MobSpawnType> WORLD_SPAWN_TYPES = EnumSet.of(
            MobSpawnType.NATURAL,
            MobSpawnType.CHUNK_GENERATION,
            MobSpawnType.SPAWNER,
            MobSpawnType.TRIAL_SPAWNER,
            MobSpawnType.STRUCTURE,
            MobSpawnType.MOB_SUMMONED,
            MobSpawnType.JOCKEY,
            MobSpawnType.EVENT,
            MobSpawnType.REINFORCEMENT,
            MobSpawnType.TRIGGERED,
            MobSpawnType.PATROL
    );

    private ZombieNightSpawnService() {}

    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (!AftermathServerConfig.ENABLE_ZOMBIE_NIGHT_ONLY_SPAWNING.get()) return;
        if (event.isCanceled() || event.isSpawnCancelled()) return;

        Mob mob = event.getEntity();
        if (!(mob.level() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(Level.OVERWORLD)) return;
        if (!WORLD_SPAWN_TYPES.contains(event.getSpawnType())) return;
        if (!ZombieEntityClassifier.isZombieLikeKillTarget(mob)) return;
        if (ZombieSpawnTime.isNight(level)) return;

        event.setSpawnCancelled(true);
        AftermathMod.LOGGER.debug(
                "Blocked daytime zombie spawn: entity={}, type={}, time={}, dimension={}.",
                mob.getType(), event.getSpawnType(),
                Math.floorMod(level.getDayTime(), ZombieSpawnTime.DAY_LENGTH_TICKS),
                level.dimension().location());
    }
}
