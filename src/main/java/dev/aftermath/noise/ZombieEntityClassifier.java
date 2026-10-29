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
 * Classifies vanilla and common modded zombie-like mobs. Datapack tags are primary;
 * ID-name matching is a fallback for modpacks without explicit entity tags.
 */
public final class ZombieEntityClassifier {
    private static final TagKey<EntityType<?>> DEFAULT_MEMBERS = TagKey.create(
            Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("aftermath", "horde_members"));

    /** Living zombie-like mobs that may react to sounds or receive Aftermath horde orders. */
    public static boolean isZombieLike(Mob mob) {
        return mob != null && !mob.isRemoved() && mob.isAlive() && isZombieLikeType(mob);
    }

    /**
     * EntityJoinLevel can fire before a mob's first fully alive tick. Goal installation must
     * classify the entity type without depending on current health.
     */
    public static boolean isZombieLikeType(Mob mob) {
        return mob != null && !mob.isRemoved() && matchesZombieLike(mob);
    }

    /**
     * Kill events are fired after health reaches zero, so this method deliberately does not
     * require the victim to still be alive.
     */
    public static boolean isZombieLikeKillTarget(Mob mob) {
        return isZombieLikeType(mob);
    }

    private static boolean matchesZombieLike(Mob mob) {
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

    public static boolean isEligibleForNoise(Mob mob, TagKey<EntityType<?>> requestedTag) {
        return mob != null && (mob.getType().is(requestedTag) || isZombieLike(mob));
    }

    private ZombieEntityClassifier() {}
}
