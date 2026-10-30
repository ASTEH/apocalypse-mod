package dev.aftermath.noise.compat;

import dev.aftermath.AftermathMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Optional Zombie Awareness bridge. Only public upstream API names are referenced,
 * and all external Minecraft/JOML types are resolved reflectively.
 */
public final class ZombieAwarenessCompatibility {
    private static final String MOD_ID = "zombieawareness";
    private static final String UTIL_CLASS = "com.corosus.zombieawareness.ZAUtil";
    private static final String SENSE_TYPE_CLASS = "com.corosus.zombieawareness.EnumSenseType";
    private static final String VECTOR_CLASS = "org.joml.Vector3d";
    private static boolean warnedAboutApiFailure;

    private ZombieAwarenessCompatibility() {}

    public static void emitSoundSense(ServerLevel level, Vec3 origin, int strength) {
        if (level == null || origin == null || strength <= 0 || !ModList.get().isLoaded(MOD_ID)) return;
        try {
            ClassLoader loader = ZombieAwarenessCompatibility.class.getClassLoader();
            Class<?> utilClass = Class.forName(UTIL_CLASS, false, loader);
            Class<?> senseTypeClass = Class.forName(SENSE_TYPE_CLASS, false, loader);
            Class<?> vectorClass = Class.forName(VECTOR_CLASS, false, loader);

            Object soundType = null;
            Object[] constants = senseTypeClass.getEnumConstants();
            if (constants != null) {
                for (Object constant : constants) {
                    if (constant instanceof Enum<?> value && value.name().equals("SOUND")) {
                        soundType = constant;
                        break;
                    }
                }
            }
            if (soundType == null) throw new NoSuchFieldException("Zombie Awareness EnumSenseType.SOUND");

            Constructor<?> vectorConstructor = vectorClass.getConstructor(
                    double.class, double.class, double.class);
            Object position = vectorConstructor.newInstance(origin.x, origin.y, origin.z);
            Method spawnSense = utilClass.getMethod("spawnOrBuffSenseAtPos",
                    Level.class, vectorClass, senseTypeClass, int.class, boolean.class);
            spawnSense.invoke(null, level, position, soundType, Math.max(1, strength), true);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            if (!warnedAboutApiFailure) {
                warnedAboutApiFailure = true;
                AftermathMod.LOGGER.warn(
                        "Zombie Awareness is installed, but its optional SOUND scent API could not be resolved. "
                                + "The Aftermath integration is disabled; native Aftermath noise still works.",
                        exception);
            }
        }
    }
}
