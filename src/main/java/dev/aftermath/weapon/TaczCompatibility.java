package dev.aftermath.weapon;

import dev.aftermath.config.AftermathServerConfig;
import dev.aftermath.character.AftermathAttachments;
import dev.aftermath.character.WeaponProgressionService;
import dev.aftermath.noise.NoiseService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * Optional TaCZ 1.21.1 NeoForge compatibility.
 *
 * No TaCZ classes are linked at compile time. The adapter activates only for the known server bullet
 * entity class and resolves its public getGunId() method reflectively. Core Aftermath remains loadable
 * when TaCZ is absent or when another gun mod is installed.
 */
public final class TaczCompatibility {
    private static final String TACZ_BULLET_CLASS = "com.tacz.guns.entity.EntityKineticBullet";


    private static final String INACCURACY_CACHE_ID = "inaccuracy";
    private static final Map<Object, AccuracyState> ACCURACY_STATES = new WeakHashMap<>();
    private static final Map<Object, Object> LAST_ACCURACY_CACHE = new WeakHashMap<>();
    private static final Map<Object, ReloadClock> RELOAD_CLOCKS = new WeakHashMap<>();

    /**
     * Applies client/server firearm trait effects through TaCZ's public runtime objects.
     * Reflection is intentional: Aftermath must remain loadable without TaCZ installed.
     *
     * eagleEyeSpreadMultiplier and quickReloadDurationMultiplier are server-derived values in
     * the range 0.1..1.0. A value of 1.0 means the corresponding trait is inactive.
     */
    public static synchronized void applyFirearmTraitEffects(Entity shooter,
                                                              double eagleEyeSpreadMultiplier,
                                                              double quickReloadDurationMultiplier,
                                                              boolean weaponProgressionEnabled) {
        if (shooter == null) return;

        double spreadMultiplier = boundedDurationMultiplier(eagleEyeSpreadMultiplier);
        Object currentCache = invokeNoArgs(shooter, "getCacheProperty");
        Object previousCache = LAST_ACCURACY_CACHE.get(shooter);
        if (previousCache != null && previousCache != currentCache) {
            restoreAccuracy(previousCache);
            LAST_ACCURACY_CACHE.remove(shooter);
        }

        if (currentCache != null && spreadMultiplier < 0.999D) {
            applyInaccuracyMultiplier(currentCache, spreadMultiplier);
            LAST_ACCURACY_CACHE.put(shooter, currentCache);
        } else {
            if (currentCache != null) restoreAccuracy(currentCache);
            LAST_ACCURACY_CACHE.remove(shooter);
        }

        double reloadMultiplier = boundedDurationMultiplier(quickReloadDurationMultiplier);
        if (weaponProgressionEnabled && shooter instanceof ServerPlayer player) {
            reloadMultiplier = boundedDurationMultiplier(
                    reloadMultiplier * proficiencyReloadDurationMultiplier(player));
        }

        if (reloadMultiplier < 0.999D) {
            accelerateReload(shooter, reloadMultiplier);
        } else {
            RELOAD_CLOCKS.remove(shooter);
        }
    }

    private static double boundedDurationMultiplier(double value) {
        if (!Double.isFinite(value)) return 1.0D;
        return Math.max(0.1D, Math.min(1.0D, value));
    }

    private static void applyInaccuracyMultiplier(Object cache, double multiplier) {
        Object raw = invokeOneArg(cache, "getCache", String.class, INACCURACY_CACHE_ID);
        if (!(raw instanceof Map<?, ?> rawMap)) return;

        @SuppressWarnings("unchecked")
        Map<Object, Object> values = (Map<Object, Object>) rawMap;
        AccuracyState state = ACCURACY_STATES.get(cache);
        if (state == null || state.values != values) {
            state = new AccuracyState(values, snapshotFloatMap(values), new LinkedHashMap<>());
        } else {
            // If TaCZ re-evaluated a firing mode or attachments in-place, take only changed
            // values as the new baseline. This avoids multiplying our own previous result again.
            for (Map.Entry<Object, Object> entry : values.entrySet()) {
                if (!(entry.getValue() instanceof Number number)) continue;
                Float lastApplied = state.lastApplied.get(entry.getKey());
                if (lastApplied == null || !nearlyEqual(lastApplied, number.floatValue())) {
                    state.original.put(entry.getKey(), number.floatValue());
                }
            }
            state.original.keySet().retainAll(values.keySet());
        }

        try {
            for (Map.Entry<Object, Float> entry : state.original.entrySet()) {
                if (!values.containsKey(entry.getKey())) continue;
                float scaled = (float) (entry.getValue() * multiplier);
                values.put(entry.getKey(), Math.max(0.01F, scaled));
            }
            state.lastApplied = snapshotFloatMap(values);
            ACCURACY_STATES.put(cache, state);
        } catch (RuntimeException ignored) {
            // A future TaCZ version may expose an immutable cache. Keep the firearm usable.
            ACCURACY_STATES.remove(cache);
        }
    }

    private static void restoreAccuracy(Object cache) {
        AccuracyState state = ACCURACY_STATES.remove(cache);
        if (state == null) return;
        Object raw = invokeOneArg(cache, "getCache", String.class, INACCURACY_CACHE_ID);
        if (!(raw instanceof Map<?, ?> rawMap) || rawMap != state.values) return;

        @SuppressWarnings("unchecked")
        Map<Object, Object> values = (Map<Object, Object>) rawMap;
        try {
            for (Map.Entry<Object, Float> entry : state.original.entrySet()) {
                Object current = values.get(entry.getKey());
                Float expected = state.lastApplied.get(entry.getKey());
                // Do not overwrite a value another TaCZ integration changed after us.
                if (current instanceof Number number && expected != null
                        && nearlyEqual(expected, number.floatValue())) {
                    values.put(entry.getKey(), entry.getValue());
                }
            }
        } catch (RuntimeException ignored) {
            // Safe fallback if a future implementation returns an immutable map.
        }
    }

    private static Map<Object, Float> snapshotFloatMap(Map<?, ?> values) {
        Map<Object, Float> copy = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (value instanceof Number number) copy.put(key, number.floatValue());
        });
        return copy;
    }

    private static boolean nearlyEqual(float left, float right) {
        return Math.abs(left - right) < 0.0001F;
    }

    private static void accelerateReload(Entity shooter, double durationMultiplier) {
        Object dataHolder = invokeNoArgs(shooter, "getDataHolder");
        if (dataHolder == null) {
            RELOAD_CLOCKS.remove(shooter);
            return;
        }

        try {
            Field timestampField = dataHolder.getClass().getField("reloadTimestamp");
            long reloadTimestamp = timestampField.getLong(dataHolder);
            if (reloadTimestamp < 0L) {
                RELOAD_CLOCKS.remove(shooter);
                return;
            }

            long now = System.currentTimeMillis();
            ReloadClock previous = RELOAD_CLOCKS.get(shooter);
            if (previous == null || reloadTimestamp > previous.adjustedTimestamp + 2L) {
                RELOAD_CLOCKS.put(shooter, new ReloadClock(now, reloadTimestamp));
                return;
            }

            long elapsed = Math.max(0L, now - previous.lastObservedAt);
            // Don't turn a long server pause into an unbounded one-tick timestamp jump.
            elapsed = Math.min(elapsed, 250L);
            long extraElapsed = Math.max(0L,
                    Math.round(elapsed * ((1.0D / durationMultiplier) - 1.0D)));
            if (extraElapsed > 0L) {
                reloadTimestamp -= extraElapsed;
                timestampField.setLong(dataHolder, reloadTimestamp);
            }
            RELOAD_CLOCKS.put(shooter, new ReloadClock(now, reloadTimestamp));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // If TaCZ changes its holder API, simply leave its normal reload timing untouched.
            RELOAD_CLOCKS.remove(shooter);
        }
    }

    /**
     * Firearm mastery adds a modest server-authoritative reload benefit at levels 2, 5 and 10.
     * The held TaCZ gun is resolved from TaCZ's active-gun supplier, not from a guessed item ID.
     */
    private static double proficiencyReloadDurationMultiplier(ServerPlayer player) {
        Object dataHolder = invokeNoArgs(player, "getDataHolder");
        Object currentGun = publicFieldValue(dataHolder, "currentGunItem");
        if (!(currentGun instanceof Supplier<?> supplier)) return 1.0D;
        Object supplied = supplier.get();
        if (!(supplied instanceof ItemStack stack) || stack.isEmpty()) return 1.0D;

        ResourceLocation gunId = gunIdFromHeldItem(stack);
        if (gunId == null || "empty".equals(gunId.getPath())) return 1.0D;
        String weaponClass = FirearmDefinitions.classForGun(gunId);
        if (weaponClass == null) return 1.0D;

        int xp = player.getData(AftermathAttachments.WEAPON_PROGRESS).experienceFor(weaponClass);
        int level = WeaponProgressionService.levelFor(xp);
        if (level >= 10) return 0.90D;
        if (level >= 5) return 0.95D;
        if (level >= 2) return 0.98D;
        return 1.0D;
    }

    private static Object publicFieldValue(Object target, String fieldName) {
        if (target == null) return null;
        try {
            Field field = target.getClass().getField(fieldName);
            return field.get(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Object invokeNoArgs(Object target, String methodName) {
        if (target == null) return null;
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Object invokeOneArg(Object target, String methodName, Class<?> argumentType, Object argument) {
        if (target == null) return null;
        try {
            return target.getClass().getMethod(methodName, argumentType).invoke(target, argument);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static final class AccuracyState {
        private final Map<?, ?> values;
        private final Map<Object, Float> original;
        private Map<Object, Float> lastApplied;

        private AccuracyState(Map<?, ?> values, Map<Object, Float> original,
                              Map<Object, Float> lastApplied) {
            this.values = values;
            this.original = new LinkedHashMap<>(original);
            this.lastApplied = new LinkedHashMap<>(lastApplied);
        }
    }

    private record ReloadClock(long lastObservedAt, long adjustedTimestamp) {}

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
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !AftermathServerConfig.ENABLE_NOISE_EVENTS.get()) return;
        if (!isTaczBullet(event.getEntity())) return;
        if (!(event.getEntity() instanceof Projectile projectile)
                || !(projectile.getOwner() instanceof ServerPlayer shooter)) return;

        NoiseService.emitNoise(level, new Vec3(shooter.getX(), shooter.getY(), shooter.getZ()),
                NoiseService.gunshotEventId(), shooter.getUUID());
    }
}
