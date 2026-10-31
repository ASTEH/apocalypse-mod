package dev.aftermath.zombie;

import dev.aftermath.AftermathMod;
import dev.aftermath.config.AftermathServerConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Optional, non-destructive door interaction for tagged zombie mobs.
 *
 * The vanilla OpenDoorGoal is used so door state changes, sounds, upper/lower door
 * halves and path interaction follow Minecraft's own implementation. No block is broken.
 */
public final class ZombieDoorInteractionService {
    private static final TagKey<EntityType<?>> ZOMBIE_DOOR_USERS = TagKey.create(
            Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "horde_members"));
    private static final Map<Mob, Boolean> INSTALLED = new WeakHashMap<>();

    private ZombieDoorInteractionService() {}

    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel)
                || !(event.getEntity() instanceof Mob mob)
                || !mob.getType().is(ZOMBIE_DOOR_USERS)
                || !AftermathServerConfig.ENABLE_ZOMBIE_DOOR_OPENING.get()) {
            return;
        }

        // Only ground navigators support the vanilla wooden-door pathfinding flags.
        if (!(mob.getNavigation() instanceof GroundPathNavigation navigation)) return;
        if (INSTALLED.putIfAbsent(mob, Boolean.TRUE) != null) return;

        navigation.setCanOpenDoors(true);
        navigation.setCanPassDoors(true);
        // false means the mob leaves the door open after passing through it.
        mob.goalSelector.addGoal(1, new OpenDoorGoal(mob, false));
        AftermathMod.LOGGER.debug("Enabled wooden-door opening for zombie entity {}.", mob.getType());
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        INSTALLED.clear();
    }
}
