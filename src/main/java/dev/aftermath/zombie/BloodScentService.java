package dev.aftermath.zombie;

import dev.aftermath.character.AftermathAttachments;
import dev.aftermath.config.AftermathServerConfig;
import dev.aftermath.medical.BleedingStatus;
import dev.aftermath.noise.NoiseService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lightweight blood-scent trails. Injury creates a scent spot immediately and an active bleed
 * leaves more spots over time; the existing Aftermath attraction director makes nearby zombies
 * investigate the last blood locations. No chunks are loaded by this service.
 */
public final class BloodScentService {
    private static final Map<ResourceLocation, List<BloodSpot>> SPOTS_BY_DIMENSION = new HashMap<>();
    private static final Map<UUID, Long> LAST_DROP_BY_PLAYER = new HashMap<>();

    private BloodScentService() {}

    /** Damage from any source can leave a scent marker, not only damage caused by hostile mobs. */
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        if (!AftermathServerConfig.ENABLE_BLOOD_SCENT.get()
                || !(event.getEntity() instanceof ServerPlayer player)
                || event.getNewDamage() <= 0.0F) {
            return;
        }
        dropIfReady(player, player.serverLevel(), 10L);
    }

    /** A player who remains bleeding leaves a trail at a configurable interval. */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)
                || !AftermathServerConfig.ENABLE_BLOOD_SCENT.get()) {
            return;
        }
        BleedingStatus bleeding = player.getData(AftermathAttachments.BLEEDING_STATUS);
        if (!bleeding.isBleeding()) return;
        dropIfReady(player, level, AftermathServerConfig.BLOOD_SCENT_DROP_INTERVAL_TICKS.get());
    }

    private static void dropIfReady(ServerPlayer player, ServerLevel level, long minimumInterval) {
        long now = level.getGameTime();
        Long previous = LAST_DROP_BY_PLAYER.get(player.getUUID());
        if (previous != null && now >= previous && now - previous < minimumInterval) return;

        ResourceLocation dimension = level.dimension().location();
        List<BloodSpot> spots = SPOTS_BY_DIMENSION.computeIfAbsent(dimension, ignored -> new ArrayList<>());
        long expiry = now + AftermathServerConfig.BLOOD_SCENT_LIFETIME_TICKS.get();

        // Merge only an immediate duplicate (e.g. damage and bleed tick in the same instant).
        // Older positions remain behind as a genuine trail for pursuing mobs.
        for (int i = spots.size() - 1; i >= 0; i--) {
            BloodSpot existing = spots.get(i);
            if (existing.ownerId.equals(player.getUUID())
                    && now - existing.createdAt <= 2L
                    && existing.position.distanceToSqr(player.position()) <= 1.0D) {
                existing.expiresAt = expiry;
                existing.nextPulseAt = Math.min(existing.nextPulseAt, now);
                LAST_DROP_BY_PLAYER.put(player.getUUID(), now);
                return;
            }
        }

        int maxSpots = AftermathServerConfig.MAX_BLOOD_SCENT_SPOTS.get();
        while (spots.size() >= maxSpots) spots.remove(0);
        spots.add(new BloodSpot(UUID.randomUUID(), player.getUUID(), player.position(), now, expiry, now));
        LAST_DROP_BY_PLAYER.put(player.getUUID(), now);
    }

    /** Staggered attraction pulses allow newly nearby zombies to discover older blood spots. */
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        ResourceLocation dimension = level.dimension().location();
        List<BloodSpot> spots = SPOTS_BY_DIMENSION.get(dimension);
        if (spots == null) return;

        long now = level.getGameTime();
        Iterator<BloodSpot> iterator = spots.iterator();
        int pulses = 0;
        int maxPulses = AftermathServerConfig.MAX_BLOOD_SCENT_PULSES_PER_TICK.get();
        long pulseInterval = Math.max(80L, AftermathServerConfig.BLOOD_SCENT_DROP_INTERVAL_TICKS.get());
        while (iterator.hasNext()) {
            BloodSpot spot = iterator.next();
            if (now >= spot.expiresAt) {
                iterator.remove();
                continue;
            }
            if (pulses >= maxPulses) continue;
            if (now < spot.nextPulseAt) continue;

            spot.nextPulseAt = now + pulseInterval;
            NoiseService.emitNoise(level, spot.position, NoiseService.bloodScentEventId(), spot.id);
            pulses++;
        }
        if (spots.isEmpty()) SPOTS_BY_DIMENSION.remove(dimension);
    }

    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_DROP_BY_PLAYER.remove(event.getEntity().getUUID());
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        SPOTS_BY_DIMENSION.clear();
        LAST_DROP_BY_PLAYER.clear();
    }

    private static final class BloodSpot {
        private final UUID id;
        private final UUID ownerId;
        private final Vec3 position;
        private final long createdAt;
        private long expiresAt;
        private long nextPulseAt;

        private BloodSpot(UUID id, UUID ownerId, Vec3 position, long createdAt,
                          long expiresAt, long nextPulseAt) {
            this.id = id;
            this.ownerId = ownerId;
            this.position = position;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
            this.nextPulseAt = nextPulseAt;
        }
    }
}
