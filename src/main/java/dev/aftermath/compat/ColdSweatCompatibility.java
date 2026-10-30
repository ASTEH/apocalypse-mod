package dev.aftermath.compat;

import dev.aftermath.AftermathMod;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

/**
 * Optional Cold Sweat bridge. Reflection keeps Aftermath loadable without Cold Sweat
 * and avoids pinning this mod to a single Cold Sweat release at compile time.
 */
public final class ColdSweatCompatibility {
    private static final String TEMPERATURE_CLASS =
            "com.momosoftworks.coldsweat.api.util.Temperature";
    private static volatile boolean warned;

    private ColdSweatCompatibility() {}

    /** Reads Cold Sweat's BODY trait (-150..150; 0 is neutral). NaN means unavailable. */
    public static double getBodyTemperature(LivingEntity entity) {
        if (!ModList.get().isLoaded("cold_sweat")) return Double.NaN;
        try {
            Class<?> temperature = Class.forName(TEMPERATURE_CLASS);
            Method getter = findBodyGetter(temperature, entity);
            Class<?> traitType = getter.getParameterTypes()[1];
            Object bodyTrait = findEnumConstant(traitType, "BODY");
            Object result = getter.invoke(null, entity, bodyTrait);
            return result instanceof Number number ? number.doubleValue() : Double.NaN;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            if (!warned) {
                warned = true;
                AftermathMod.LOGGER.warn(
                        "Cold Sweat is installed but its Temperature.get(entity, BODY) API could not be resolved. "
                                + "The Aftermath temperature Moodle will remain inactive. Cause: {}",
                        exception.toString());
            }
            return Double.NaN;
        }
    }

    private static Method findBodyGetter(Class<?> temperature, LivingEntity entity)
            throws NoSuchMethodException {
        for (Method method : temperature.getMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (method.getName().equals("get")
                    && java.lang.reflect.Modifier.isStatic(method.getModifiers())
                    && parameters.length == 2
                    && parameters[0].isAssignableFrom(entity.getClass())
                    && parameters[1].isEnum()
                    && parameters[1].getSimpleName().equals("Trait")) {
                return method;
            }
        }
        throw new NoSuchMethodException("Temperature.get(LivingEntity, Trait)");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object findEnumConstant(Class<?> enumType, String expected) {
        if (!enumType.isEnum()) throw new IllegalArgumentException("Cold Sweat Trait is not an enum");
        for (Object constant : enumType.getEnumConstants()) {
            if (((Enum) constant).name().equalsIgnoreCase(expected)) return constant;
        }
        throw new IllegalArgumentException("Cold Sweat BODY trait was not found");
    }
}
