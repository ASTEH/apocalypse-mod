package dev.aftermath.parkour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Short, server-computed parkour bursts. Every intermediate player-sized box is checked
 * against loaded-world collision before the server teleports the player to a safe landing.
 */
public final class ParkourService {
    private static final long COOLDOWN_TICKS = 10L;
    private static final double MIN_HORIZONTAL_SPEED_SQR = 0.0025D;
    private static final double MAX_TRAVEL_DISTANCE = 1.8D;
    private static final double MAX_MANTLE_HEIGHT = 1.15D;
    private static final double VAULT_ARC_HEIGHT = 1.65D;
    private static final Map<UUID, Long> LAST_ACTION = new HashMap<>();

    private ParkourService() {}

    public static void request(ServerPlayer player) {
        if (!player.isAlive() || player.isPassenger() || player.isFallFlying()
                || player.isInWaterOrBubble() || player.isInLava()
                || player.isSpectator() || !player.onGround() || !player.isSprinting()) {
            return;
        }

        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        long previous = LAST_ACTION.getOrDefault(player.getUUID(), Long.MIN_VALUE);
        if (previous != Long.MIN_VALUE && now - previous < COOLDOWN_TICKS) return;
        LAST_ACTION.put(player.getUUID(), now);

        Vec3 movement = player.getDeltaMovement();
        Vec3 horizontal = new Vec3(movement.x, 0.0D, movement.z);
        if (horizontal.lengthSqr() < MIN_HORIZONTAL_SPEED_SQR) return;
        Vec3 direction = horizontal.normalize();

        // A low opening can be crossed while crouching; all other moves require the
        // standing collision box. The player's current pose is always used by the server.
        if (player.isCrouching() && tryCrawlThroughOpening(player, level, direction)) return;
        tryVaultOrMantle(player, level, direction);
    }

    private static boolean tryCrawlThroughOpening(ServerPlayer player, ServerLevel level, Vec3 direction) {
        AABB crouchedBox = player.getBoundingBox();
        AABB standingBox = standingBox(player);
        Vec3 origin = player.position();
        double distance = 1.35D;
        boolean standingBlocked = false;

        for (int step = 1; step <= 8; step++) {
            double t = step / 8.0D;
            double dx = direction.x * distance * t;
            double dz = direction.z * distance * t;
            AABB crawlAt = crouchedBox.move(dx, 0.0D, dz);
            AABB standAt = standingBox.move(dx, 0.0D, dz);
            if (!hasLoadedSpace(level, crawlAt) || !level.noCollision(player, crawlAt)) return false;
            if (!hasLoadedSpace(level, standAt) || !level.noCollision(player, standAt)) standingBlocked = true;
        }
        if (!standingBlocked) return false;

        double targetX = origin.x + direction.x * distance;
        double targetZ = origin.z + direction.z * distance;
        if (!hasSupport(level, targetX, origin.y, targetZ)) return false;
        player.teleportTo(targetX, origin.y, targetZ);
        player.setDeltaMovement(direction.scale(0.08D));
        player.causeFoodExhaustion(0.04F);
        return true;
    }

    private static void tryVaultOrMantle(ServerPlayer player, ServerLevel level, Vec3 direction) {
        double[] distances = {1.25D, 1.55D, MAX_TRAVEL_DISTANCE};
        double[] rises = {1.0D, 0.55D, 0.0D};

        // Prefer a low mantle when a raised landing exists, otherwise vault back onto
        // the current floor. Each candidate is checked as a continuous sampled arc.
        for (double distance : distances) {
            for (double rise : rises) {
                if (rise > MAX_MANTLE_HEIGHT) continue;
                double arcHeight = rise > 0.0D ? Math.max(0.7D, rise + 0.25D) : VAULT_ARC_HEIGHT;
                Candidate candidate = validateArc(player, level, direction, distance, rise, arcHeight);
                if (candidate == null || !candidate.straightPathWasBlocked()) continue;

                player.teleportTo(candidate.x(), candidate.y(), candidate.z());
                player.setDeltaMovement(direction.scale(0.10D).add(0.0D, 0.06D, 0.0D));
                player.causeFoodExhaustion(0.08F);
                return;
            }
        }
    }

    private static Candidate validateArc(ServerPlayer player, ServerLevel level, Vec3 direction,
                                         double distance, double rise, double arcHeight) {
        Vec3 origin = player.position();
        AABB box = player.getBoundingBox();
        boolean straightBlocked = false;

        // 12 samples keep the largest horizontal gap below 0.15 blocks. A wall cannot
        // be crossed merely because the final destination happens to be collision-free.
        for (int step = 1; step <= 12; step++) {
            double t = step / 12.0D;
            double dx = direction.x * distance * t;
            double dz = direction.z * distance * t;
            double easedRise = rise * t * t * (3.0D - 2.0D * t);
            double y = easedRise + Math.sin(Math.PI * t) * arcHeight;
            AABB arcAt = box.move(dx, y, dz);
            AABB straightAt = box.move(dx, easedRise, dz);

            if (!hasLoadedSpace(level, straightAt)) return null;
            if (!level.noCollision(player, straightAt)) straightBlocked = true;
            if (!hasLoadedSpace(level, arcAt) || !level.noCollision(player, arcAt)) return null;
        }

        double targetX = origin.x + direction.x * distance;
        double targetY = origin.y + rise;
        double targetZ = origin.z + direction.z * distance;
        AABB landingBox = box.move(direction.x * distance, rise, direction.z * distance);
        if (!hasLoadedSpace(level, landingBox) || !level.noCollision(player, landingBox)) return null;
        if (!hasSupport(level, targetX, targetY, targetZ)) return null;
        return new Candidate(targetX, targetY, targetZ, straightBlocked);
    }

    private static boolean hasLoadedSpace(ServerLevel level, AABB box) {
        int minX = net.minecraft.util.Mth.floor(box.minX);
        int maxX = net.minecraft.util.Mth.floor(box.maxX);
        int minZ = net.minecraft.util.Mth.floor(box.minZ);
        int maxZ = net.minecraft.util.Mth.floor(box.maxZ);
        return level.hasChunkAt(new BlockPos(minX, net.minecraft.util.Mth.floor(box.minY), minZ))
                && level.hasChunkAt(new BlockPos(maxX, net.minecraft.util.Mth.floor(box.maxY), maxZ));
    }

    private static boolean hasSupport(ServerLevel level, double x, double y, double z) {
        BlockPos support = BlockPos.containing(x, y - 0.05D, z);
        return level.hasChunkAt(support)
                && level.getBlockState(support).isFaceSturdy(level, support, Direction.UP);
    }

    private static AABB standingBox(Player player) {
        double halfWidth = player.getBbWidth() / 2.0D;
        return new AABB(player.getX() - halfWidth, player.getY(), player.getZ() - halfWidth,
                player.getX() + halfWidth, player.getY() + 1.8D, player.getZ() + halfWidth);
    }

    public static void clear() {
        LAST_ACTION.clear();
    }

    private record Candidate(double x, double y, double z, boolean straightPathWasBlocked) {}
}
