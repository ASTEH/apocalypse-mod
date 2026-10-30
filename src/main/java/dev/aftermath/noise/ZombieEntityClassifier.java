package dev.aftermath.noise;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;

import java.util.Locale;

/**
 * Identifies vanilla and modded zombie-like hostile mobs for Aftermath systems.
 * Datapack tags provide an explicit override; registry-name matching is the fallback.
 */
public final class ZombieEntityClassifier {
    private static final TagKey<EntityType<?>> DEFAULT_MEMBERS = TagKey.create(
            Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("aftermath", "horde_members"));

    public static boolean isZombieLikeKillTarget(Mob mob) {
        if (mob == null || mob.isRemoved()) return false;
        if (mob.getType().is(DEFAULT_MEMBERS)) return true;
        if (!(mob instanceof Enemy)) return false;

        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        if (id == null) return false;
        String path = (id.getNamespace() + ":" + id.getPath()).toLowerCase(Locale.ROOT);
        return path.contains("zombie")
                || path.contains("infected")
                || path.contains("walker")
                || path.contains("shambler")
                || path.contains("crawler")
                || path.contains("ghoul")
                || path.contains("living_dead");
    }

    private ZombieEntityClassifier() {}
}
