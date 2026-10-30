package dev.aftermath.weapon;

import dev.aftermath.config.AftermathServerConfig;
import dev.aftermath.noise.NoiseService;
import dev.aftermath.state.PlayerStateManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.lang.reflect.Method;

/**
 * Optional TaCZ 1.21.1 NeoForge compatibility.
 *
 * No TaCZ classes are linked at compile time. The adapter activates only for the known server bullet
 * entity class and resolves its public getGunId() method reflectively. Core Aftermath remains loadable
 * when TaCZ is absent or when another gun mod is installed.
 */
public final class TaczCompatibility {
    private static final String TACZ_BULLET_CLASS = "com.tacz.guns.entity.EntityKineticBullet";

    private TaczCompatibility() {}

    public static boolean isTaczBullet(Entity entity) {
        return entity != null && entity.getClass().getName().equals(TACZ_BULLET_CLASS);
    }

    public static ResourceLocation gunId(Entity entity) {
        if (!isTaczBullet(entity)) return null;
        try {
            Method method = entity.getClass().getMethod("getGunId");
            Object result = method.invoke(entity);
            if (result instanceof ResourceLocation id) return id;
            if (result instanceof String value) return ResourceLocation.tryParse(value);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Unknown TaCZ API variant: no projectile XP can be attributed safely.
        }
        return null;
    }

    /**
     * Reads the TaCZ gun ID from the item held in the main hand without linking to TaCZ classes.
     * Used by the diagnostic command so custom gunpack IDs can be mapped precisely.
     */
    public static ResourceLocation gunIdFromHeldItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        try {
            Object item = stack.getItem();
            Method method = item.getClass().getMethod("getGunId", ItemStack.class);
            Object result = method.invoke(item, stack);
            if (result instanceof ResourceLocation id) return id;
            if (result instanceof String value) return ResourceLocation.tryParse(value);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Items that do not expose the TaCZ gun-ID API are not TaCZ guns.
        }
        return null;
    }

    public static String weaponClassForBullet(Entity entity, ServerPlayer expectedShooter) {
        if (!isTaczBullet(entity) || !(entity instanceof Projectile projectile)) return null;
        if (projectile.getOwner() != expectedShooter) return null;
        return FirearmDefinitions.classForGun(gunId(entity));
    }

    /**
     * A server-side kinetic bullet is created only when a gun actually fires. Use that event to
     * report noise; a 20-tick per-player cooldown in NoiseService prevents shotgun volleys from
     * producing duplicate noise scans.
     */
    private static void applyPanicSpread(Projectile projectile, ServerPlayer shooter) {
        double spread = PlayerStateManager.effect(shooter, "firearm_spread_additive", 0.0D);
        if (spread <= 0.0D) return;

        Vec3 velocity = projectile.getDeltaMovement();
        double speed = velocity.length();
        if (speed < 0.0001D) return;

        Vec3 forward = velocity.scale(1.0D / speed);
        Vec3 reference = Math.abs(forward.y) < 0.9D
                ? new Vec3(0.0D, 1.0D, 0.0D)
                : new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 right = forward.cross(reference).normalize();
        Vec3 up = right.cross(forward).normalize();
        double deviation = Math.min(0.12D, spread * 0.12D);
        double horizontal = shooter.getRandom().nextGaussian() * deviation;
        double vertical = shooter.getRandom().nextGaussian() * deviation;
        Vec3 adjusted = forward.add(right.scale(horizontal)).add(up.scale(vertical)).normalize();
        projectile.setDeltaMovement(adjusted.scale(speed));
    }

    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!isTaczBullet(event.getEntity())) return;
        if (!(event.getEntity() instanceof Projectile projectile)
                || !(projectile.getOwner() instanceof ServerPlayer shooter)) return;

        // Moodles affect shot stability on the authoritative server using the projectile velocity.
        applyPanicSpread(projectile, shooter);
        PlayerStateManager.triggerEvent(shooter, "gunshot");
        if (AftermathServerConfig.ENABLE_NOISE_EVENTS.get()) {
            NoiseService.emitNoise(level, new Vec3(shooter.getX(), shooter.getY(), shooter.getZ()),
                    NoiseService.gunshotEventId(), shooter.getUUID());
        }
    }
}
