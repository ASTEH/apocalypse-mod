package dev.aftermath.world;

import dev.aftermath.AftermathMod;
import dev.aftermath.config.AftermathServerConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Optional, reflection-only adapter for TaCZ: Simple Enemy.
 *
 * Simple Enemy stores a per-player, per-event probability in its EventProbabilityData SavedData.
 * Its dynamic events create whole squads after the probability check. Clamping those saved
 * probabilities keeps the squads intact and avoids touching recruited PMC allies, spawn eggs,
 * or unrelated NPC entities.
 */
public final class MilitarySpawnControlService {
    private static final String SIMPLE_ENEMY_MOD_ID = "simpleenemymod";
    private static final long CHECK_INTERVAL_TICKS = 20L;

    private static final MilitaryEvent[] EVENTS = {
            new MilitaryEvent("military_patrol", "PATROL_BASE_CHANCE", 0.10D),
            new MilitaryEvent("far_combat", "COMBAT_BASE_CHANCE", 0.08D),
            new MilitaryEvent("cave_extraction", "CAVE_BASE_CHANCE", 0.02D)
    };

    private static volatile ReflectionBridge bridge;
    private static volatile boolean bridgeUnavailableLogged;
    private static volatile boolean modMissingLogged;

    private MilitarySpawnControlService() {}

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!AftermathServerConfig.ENABLE_MILITARY_SPAWN_CONTROL.get()) return;
        if (!ModList.get().isLoaded(SIMPLE_ENEMY_MOD_ID)) {
            if (!modMissingLogged) {
                AftermathMod.LOGGER.info("TaCZ: Simple Enemy is not installed; military event frequency control is idle.");
                modMissingLogged = true;
            }
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)
                || !level.dimension().equals(Level.OVERWORLD)
                || level.getGameTime() % CHECK_INTERVAL_TICKS != 0L) {
            return;
        }

        double multiplier = AftermathServerConfig.MILITARY_EVENT_CHANCE_MULTIPLIER.get();
        if (multiplier >= 1.0D) return;

        ReflectionBridge adapter = getBridge();
        if (adapter == null) return;

        try {
            Object probabilityData = adapter.getData.invoke(null, level);
            if (probabilityData == null) return;

            for (ServerPlayer player : level.players()) {
                String playerId = player.getUUID().toString();
                for (MilitaryEvent eventDefinition : EVENTS) {
                    double baseChance = adapter.baseChance(eventDefinition);
                    double maximumChance = clampChance(baseChance * multiplier);
                    Object currentValue = adapter.getChance.invoke(
                            probabilityData, eventDefinition.id(), playerId, baseChance);
                    if (!(currentValue instanceof Number number)) continue;

                    double currentChance = number.doubleValue();
                    if (!Double.isFinite(currentChance) || currentChance > maximumChance + 1.0E-9D) {
                        adapter.setChance.invoke(
                                probabilityData, eventDefinition.id(), playerId, maximumChance);
                    }
                }
            }
        } catch (ReflectiveOperationException | LinkageError exception) {
            bridge = null;
            if (!bridgeUnavailableLogged) {
                AftermathMod.LOGGER.warn(
                        "Could not apply the optional Simple Enemy event probability limit. "
                                + "Simple Enemy will keep its own spawn rates; Aftermath remains operational.",
                        exception);
                bridgeUnavailableLogged = true;
            }
        }
    }

    private static ReflectionBridge getBridge() {
        ReflectionBridge cached = bridge;
        if (cached != null) return cached;
        if (bridgeUnavailableLogged) return null;

        synchronized (MilitarySpawnControlService.class) {
            if (bridge != null) return bridge;
            if (bridgeUnavailableLogged) return null;

            try {
                ClassLoader loader = MilitarySpawnControlService.class.getClassLoader();
                Class<?> dataClass = Class.forName(
                        "net.nekoyuni.SimpleEnemyMod.world.data.EventProbabilityData", false, loader);
                Method getData = dataClass.getMethod("get", ServerLevel.class);
                Method getChance = dataClass.getMethod(
                        "getChance", String.class, String.class, double.class);
                Method setChance = dataClass.getMethod(
                        "setChance", String.class, String.class, double.class);

                Class<?> configClass = null;
                try {
                    configClass = Class.forName(
                            "net.nekoyuni.SimpleEnemyMod.config.common.EventSpawnConfig", false, loader);
                } catch (ClassNotFoundException ignored) {
                    AftermathMod.LOGGER.warn(
                            "Simple Enemy EventSpawnConfig was not found; using its known default event chances.");
                }

                bridge = new ReflectionBridge(getData, getChance, setChance, configClass);
                AftermathMod.LOGGER.info(
                        "Connected to TaCZ: Simple Enemy event probability data through optional reflection.");
                return bridge;
            } catch (ReflectiveOperationException | LinkageError exception) {
                if (!bridgeUnavailableLogged) {
                    AftermathMod.LOGGER.warn(
                            "TaCZ: Simple Enemy is installed, but its event probability API could not be resolved. "
                                    + "Military event frequency control is disabled; Aftermath remains operational.",
                            exception);
                    bridgeUnavailableLogged = true;
                }
                return null;
            }
        }
    }

    private static double clampChance(double chance) {
        if (!Double.isFinite(chance)) return 0.0D;
        return Math.max(0.0D, Math.min(1.0D, chance));
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        bridge = null;
        bridgeUnavailableLogged = false;
        modMissingLogged = false;
    }

    private record MilitaryEvent(String id, String configField, double fallbackBaseChance) {}

    private static final class ReflectionBridge {
        private final Method getData;
        private final Method getChance;
        private final Method setChance;
        private final Class<?> configClass;

        private ReflectionBridge(Method getData, Method getChance, Method setChance, Class<?> configClass) {
            this.getData = getData;
            this.getChance = getChance;
            this.setChance = setChance;
            this.configClass = configClass;
        }

        private double baseChance(MilitaryEvent eventDefinition) {
            if (configClass == null) return eventDefinition.fallbackBaseChance();
            try {
                Field field = configClass.getField(eventDefinition.configField());
                Object configValue = field.get(null);
                if (configValue == null) return eventDefinition.fallbackBaseChance();
                Method getter = configValue.getClass().getMethod("get");
                Object value = getter.invoke(configValue);
                if (value instanceof Number number && Double.isFinite(number.doubleValue())) {
                    return clampChance(number.doubleValue());
                }
            } catch (ReflectiveOperationException | LinkageError exception) {
                AftermathMod.LOGGER.debug(
                        "Could not read Simple Enemy config field {}; using fallback {}.",
                        eventDefinition.configField(), eventDefinition.fallbackBaseChance());
            }
            return eventDefinition.fallbackBaseChance();
        }
    }
}
