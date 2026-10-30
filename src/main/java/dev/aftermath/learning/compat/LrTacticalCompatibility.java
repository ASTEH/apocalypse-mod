package dev.aftermath.learning.compat;

import dev.aftermath.AftermathMod;
import dev.aftermath.learning.LearningService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Optional LRTactical medical-consumable learning bridge.
 *
 * LRTactical is not a compile dependency. Its server-side ConsumableUseEvent is discovered
 * only when the mod is installed, and its public data API is read reflectively.
 */
public final class LrTacticalCompatibility {
    private static final String MOD_ID = "lrtactical";
    private static final String EVENT_CLASS = "me.xjqsh.lrtactical.api.event.ConsumableUseEvent";
    private static boolean warnedAboutApiFailure;

    private LrTacticalCompatibility() {}

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void register() {
        if (!ModList.get().isLoaded(MOD_ID)) return;
        try {
            Class<?> rawType = Class.forName(EVENT_CLASS, false,
                    LrTacticalCompatibility.class.getClassLoader());
            Class<? extends Event> eventType = rawType.asSubclass(Event.class);
            NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false,
                    (Class) eventType, LrTacticalCompatibility::onConsumableUse);
            AftermathMod.LOGGER.info("Registered optional LRTactical medical learning adapter.");
        } catch (ReflectiveOperationException | LinkageError | ClassCastException exception) {
            AftermathMod.LOGGER.warn(
                    "LRTactical is installed, but its medical-use event API could not be resolved; "
                            + "the optional learning adapter is disabled.",
                    exception);
        }
    }

    private static void onConsumableUse(Event event) {
        try {
            Object user = invoke(event, "getUser");
            if (!(user instanceof ServerPlayer player)) return;
            Object index = invoke(event, "getIndex");
            Object data = invoke(index, "getData");
            Object rawHeal = invoke(data, "getHeal");
            float heal = rawHeal instanceof Number number ? number.floatValue() : 0.0F;
            Object rawRemovals = invoke(data, "getRemoveEffects");
            boolean removesEffects = rawRemovals instanceof List<?> removals && !removals.isEmpty();
            if (heal > 0.0F || removesEffects) {
                LearningService.awardFirstAidXp(player);
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (!warnedAboutApiFailure) {
                warnedAboutApiFailure = true;
                AftermathMod.LOGGER.warn(
                        "LRTactical medical event was received, but its data API could not be read. "
                                + "Further adapter warnings are suppressed.",
                        exception);
            }
        }
    }

    private static Object invoke(Object target, String methodName) throws ReflectiveOperationException {
        if (target == null) throw new IllegalStateException("LRTactical event returned null from " + methodName);
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }
}
