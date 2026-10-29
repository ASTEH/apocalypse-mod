package dev.aftermath.zombie;

import dev.aftermath.config.AftermathServerConfig;
import dev.aftermath.noise.NoiseService;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Finds exposed artificial light without loading chunks and feeds sightings into Aftermath's
 * existing attraction director. Placed light sources are tracked immediately; a throttled local
 * scan also discovers lamps already present in chunks when this mod starts.
 */
public final class LightSourceAwarenessService {
    private static final Map<ResourceLocation, LinkedHashMap<Long, TrackedLight>> SOURCES = new HashMap<>();

    private LightSourceAwarenessService() {}

    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) return;
        trackOrRemove(level, event.getPos(), level.getGameTime());
    }

    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) return;
        remove(level, event.getPos());
    }

    /** Neighbor updates cover common on/off light states such as redstone lamps. */
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        trackOrRemove(level, event.getPos(), level.getGameTime());
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !AftermathServerConfig.ENABLE_LIGHT_SOURCE_AWARENESS.get()) {
            return;
        }

        long now = level.getGameTime();
        if (now % 200L == 0L) {
            pruneUnloadedOrDarkSources(level);
            scanNearOnePlayer(level, now);
        }
        checkDueSources(level, now);
    }

    private static void scanNearOnePlayer(ServerLevel level, long now) {
        var players = level.players();
        if (players.isEmpty()) return;

        int playerIndex = (int) Math.floorMod(now / 200L, players.size());
        ServerPlayer player = players.get(playerIndex);
        BlockPos center = player.blockPosition();
        int radius = AftermathServerConfig.LIGHT_SOURCE_SCAN_RADIUS.get();
        int minY = Math.max(level.getMinBuildHeight(), center.getY() - radius);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, center.getY() + radius);

        // Only inspect already-loaded horizontal columns. Checking each column once avoids repeated
        // chunk lookups for every y-coordinate and this routine runs only once per 10 seconds.
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                if (!level.hasChunkAt(new BlockPos(x, center.getY(), z))) continue;
                BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, minY, z);
                for (int y = minY; y <= maxY; y++) {
                    cursor.setY(y);
                    trackOrRemove(level, cursor, now);
                }
            }
        }
    }

    private static void trackOrRemove(ServerLevel level, BlockPos pos, long now) {
        if (!level.hasChunkAt(pos)) return;
        BlockState state = level.getBlockState(pos);
        if (isDetectableLight(level, pos, state)) {
            track(level, pos, now);
        } else {
            remove(level, pos);
        }
    }

    private static boolean isDetectableLight(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir()
                || state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.LAVA)
                || !state.getFluidState().isEmpty()) {
            return false;
        }
        return state.getLightEmission(level, pos) >= AftermathServerConfig.LIGHT_SOURCE_MIN_EMISSION.get();
    }

    private static void track(ServerLevel level, BlockPos pos, long now) {
        ResourceLocation dimension = level.dimension().location();
        LinkedHashMap<Long, TrackedLight> lights =
                SOURCES.computeIfAbsent(dimension, ignored -> new LinkedHashMap<>());
        long packed = pos.asLong();
        if (lights.containsKey(packed)) return;

        int maxSources = AftermathServerConfig.MAX_TRACKED_LIGHT_SOURCES.get();
        while (lights.size() >= maxSources && !lights.isEmpty()) {
            Iterator<Long> oldest = lights.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
        lights.put(packed, new TrackedLight(now));
    }

    private static void remove(ServerLevel level, BlockPos pos) {
        ResourceLocation dimension = level.dimension().location();
        LinkedHashMap<Long, TrackedLight> lights = SOURCES.get(dimension);
        if (lights == null) return;
        lights.remove(pos.asLong());
        if (lights.isEmpty()) SOURCES.remove(dimension);
    }

    private static void pruneUnloadedOrDarkSources(ServerLevel level) {
        ResourceLocation dimension = level.dimension().location();
        LinkedHashMap<Long, TrackedLight> lights = SOURCES.get(dimension);
        if (lights == null) return;

        Iterator<Map.Entry<Long, TrackedLight>> iterator = lights.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, TrackedLight> entry = iterator.next();
            BlockPos pos = BlockPos.of(entry.getKey());
            if (!level.hasChunkAt(pos)) {
                iterator.remove();
                continue;
            }
            if (!isDetectableLight(level, pos, level.getBlockState(pos))) iterator.remove();
        }
        if (lights.isEmpty()) SOURCES.remove(dimension);
    }

    private static void checkDueSources(ServerLevel level, long now) {
        ResourceLocation dimension = level.dimension().location();
        LinkedHashMap<Long, TrackedLight> lights = SOURCES.get(dimension);
        if (lights == null || lights.isEmpty()) return;

        int budget = AftermathServerConfig.MAX_LIGHT_SOURCE_SCANS_PER_TICK.get();
        int scanned = 0;
        int interval = AftermathServerConfig.LIGHT_SOURCE_PULSE_INTERVAL_TICKS.get();
        Iterator<Map.Entry<Long, TrackedLight>> iterator = lights.entrySet().iterator();
        while (iterator.hasNext() && scanned < budget) {
            Map.Entry<Long, TrackedLight> entry = iterator.next();
            TrackedLight tracked = entry.getValue();
            if (now < tracked.nextCheckTick) continue;

            BlockPos pos = BlockPos.of(entry.getKey());
            if (!level.hasChunkAt(pos)) {
                iterator.remove();
                continue;
            }
            if (!isDetectableLight(level, pos, level.getBlockState(pos))) {
                iterator.remove();
                continue;
            }

            tracked.nextCheckTick = now + interval;
            Vec3 origin = Vec3.atCenterOf(pos);
            UUID sourceId = sourceId(dimension, entry.getKey());
            NoiseService.emitNoise(level, origin, NoiseService.lightSourceEventId(), sourceId,
                    mob -> canSeeLight(level, mob, pos));
            scanned++;
        }
        if (lights.isEmpty()) SOURCES.remove(dimension);
    }

    private static boolean canSeeLight(ServerLevel level, Mob mob, BlockPos source) {
        if (mob.isRemoved() || !mob.isAlive() || mob.isNoAi()) return false;
        Vec3 from = mob.getEyePosition();
        Vec3 center = Vec3.atCenterOf(source);
        // Aim at the source's near face, not its center. For a wall-mounted torch, aiming through
        // its center may make the ray hit the solid support block directly behind the flame,
        // falsely treating a visible torch as occluded.
        Vec3 towardMob = from.subtract(center);
        if (towardMob.lengthSqr() < 1.0E-6D) return true;
        Vec3 to = center.add(towardMob.normalize().scale(0.49D));
        BlockHitResult hit = level.clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(source);
    }

    private static UUID sourceId(ResourceLocation dimension, long packedPos) {
        byte[] bytes = (dimension + ":light:" + packedPos).getBytes(StandardCharsets.UTF_8);
        return UUID.nameUUIDFromBytes(bytes);
    }

    public static int trackedLightCount(ServerLevel level) {
        Map<Long, TrackedLight> lights = SOURCES.get(level.dimension().location());
        return lights == null ? 0 : lights.size();
    }

    /**
     * Diagnostic: force the shared light-attraction signal from the nearest tracked light within
     * 64 blocks and report how many loaded zombies can actually see that source.
     */
    public static LightTestResult testNearestSource(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        LinkedHashMap<Long, TrackedLight> lights = SOURCES.get(level.dimension().location());
        if (lights == null || lights.isEmpty()) return new LightTestResult(false, null, 0);

        long now = level.getGameTime();
        double bestDistance = 64.0D * 64.0D;
        BlockPos bestPos = null;
        for (Long packed : lights.keySet()) {
            BlockPos pos = BlockPos.of(packed);
            if (!level.hasChunkAt(pos) || !isDetectableLight(level, pos, level.getBlockState(pos))) continue;
            double distance = pos.distSqr(player.blockPosition());
            if (distance <= bestDistance) {
                bestDistance = distance;
                bestPos = pos.immutable();
            }
        }
        if (bestPos == null) return new LightTestResult(false, null, 0);

        BlockPos targetPos = bestPos;
        NoiseService.NoiseResult result = NoiseService.emitNoise(level, Vec3.atCenterOf(targetPos),
                NoiseService.lightSourceEventId(), UUID.randomUUID(), mob -> canSeeLight(level, mob, targetPos));
        return new LightTestResult(true, targetPos, result.affectedEntities());
    }

    public record LightTestResult(boolean foundSource, BlockPos source, int affectedEntities) {}

    public static void onServerStopping(ServerStoppingEvent event) {
        SOURCES.clear();
    }

    private static final class TrackedLight {
        private long nextCheckTick;

        private TrackedLight(long firstCheckTick) {
            this.nextCheckTick = firstCheckTick;
        }
    }
}
