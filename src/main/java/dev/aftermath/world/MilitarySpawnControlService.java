package dev.aftermath.world;

import dev.aftermath.config.AftermathServerConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/** Applies the configurable rarity policy to selected military NPC spawn attempts. */
public final class MilitarySpawnControlService {
    private MilitarySpawnControlService() {}

    public static void onMobPositionCheck(MobSpawnEvent.PositionCheck event) {
        if (!AftermathServerConfig.ENABLE_MILITARY_SPAWN_CONTROL.get()) return;

        Mob mob = event.getEntity();
        if (mob.level().isClientSide()) return;

        MilitarySpawnDefinitions.Snapshot policy = MilitarySpawnDefinitions.snapshot();
        if (!policy.shouldLimit(mob, mob.level().dimension().location(),
                event.getSpawnType().name())) {
            return;
        }

        double chance = policy.spawnChance(mob.level().dimension().location(), mob.blockPosition());
        if (chance >= 1.0D) return;
        if (chance <= 0.0D || mob.getRandom().nextDouble() >= chance) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }
}
