package dev.aftermath.zombie;

import dev.aftermath.AftermathMod;
import dev.aftermath.noise.ZombieEntityClassifier;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Temporary zombie camouflage that modifies only vision-driven target acquisition.
 * Hearing/noise reactions remain active, so the player is not invisible or silent.
 */
public final class ZombieCamouflageService {
    private static final String UNTIL_TAG = "AftermathZombieCamouflageUntil";
    private static final long DURATION_TICKS = 3L * 60L * 20L;
    private static final double CLOSE_DETECTION_RANGE = 5.0D;
    private static final float DETECTION_CHANCE = 0.25F;
    private static final long RETRY_INTERVAL_TICKS = 20L;
    private static final Map<DetectionKey, Long> NEXT_ATTEMPT = new HashMap<>();

    private ZombieCamouflageService() {}

    public static void activate(ServerPlayer player) {
        long now = overworldTime(player);
        player.getPersistentData().putLong(UNTIL_TAG, now + DURATION_TICKS);
        clearDistantTargets(player);
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                "message.aftermath.camouflage.started", DURATION_TICKS / 20L));
    }

    public static boolean isActive(ServerPlayer player) {
        if (!player.getPersistentData().contains(UNTIL_TAG, Tag.TAG_LONG)) return false;
        return overworldTime(player) < player.getPersistentData().getLong(UNTIL_TAG);
    }

    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob)
                || !(event.getNewAboutToBeSetTarget() instanceof ServerPlayer player)
                || !ZombieEntityClassifier.isZombieLikeKillTarget(mob)
                || !isActive(player)
                || mob.distanceToSqr(player) <= CLOSE_DETECTION_RANGE * CLOSE_DETECTION_RANGE
                || !mob.getSensing().hasLineOfSight(player)
                || !(mob.level() instanceof ServerLevel level)) {
            return;
        }

        DetectionKey key = new DetectionKey(mob.getUUID(), player.getUUID());
        long now = level.getGameTime();
        Long next = NEXT_ATTEMPT.get(key);
        if (next != null && now < next) {
            event.setCanceled(true);
            return;
        }
        NEXT_ATTEMPT.put(key, now + RETRY_INTERVAL_TICKS);
        if (mob.getRandom().nextFloat() >= DETECTION_CHANCE) {
            event.setCanceled(true);
        }
        if (NEXT_ATTEMPT.size() > 2048) {
            NEXT_ATTEMPT.entrySet().removeIf(entry -> now >= entry.getValue());
        }
    }

    public static void onPlayerAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) deactivate(player, "attack");
    }

    public static void onPlayerDamage(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getNewDamage() > 0.0F) {
            deactivate(player, "damage");
        }
    }

    /** Called by the existing NoiseService after a loud player-originated event is accepted. */
    public static void onStrongNoise(ServerLevel level, UUID sourceId, int effectiveRadius) {
        if (sourceId == null || effectiveRadius < 28) return;
        Player entity = level.getPlayerByUUID(sourceId);
        if (entity instanceof ServerPlayer player) deactivate(player, "noise");
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.getPersistentData().contains(UNTIL_TAG, Tag.TAG_LONG)
                && !isActive(player)) {
            player.getPersistentData().remove(UNTIL_TAG);
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    "message.aftermath.camouflage.expired"));
        }
    }

    public static void deactivate(ServerPlayer player, String reason) {
        if (!player.getPersistentData().contains(UNTIL_TAG, Tag.TAG_LONG)) return;
        player.getPersistentData().remove(UNTIL_TAG);
        String key = switch (reason) {
            case "attack" -> "message.aftermath.camouflage.broken_attack";
            case "damage" -> "message.aftermath.camouflage.broken_damage";
            case "noise" -> "message.aftermath.camouflage.broken_noise";
            default -> "message.aftermath.camouflage.expired";
        };
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(key));
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        NEXT_ATTEMPT.clear();
    }

    private static void clearDistantTargets(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        AABB area = player.getBoundingBox().inflate(32.0D);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, area,
                candidate -> candidate.isAlive() && candidate.getTarget() == player
                        && candidate.distanceToSqr(player) > CLOSE_DETECTION_RANGE * CLOSE_DETECTION_RANGE
                        && ZombieEntityClassifier.isZombieLikeKillTarget(candidate))) {
            mob.setTarget(null);
        }
    }

    private static long overworldTime(ServerPlayer player) {
        return player.serverLevel().getServer().overworld().getGameTime();
    }

    private record DetectionKey(UUID mobId, UUID playerId) {}
}
