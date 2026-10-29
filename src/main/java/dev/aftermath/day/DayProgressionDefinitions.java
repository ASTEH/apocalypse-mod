package dev.aftermath.day;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aftermath.AftermathMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Loads world progression stages from data/aftermath/day_progression/default.json. */
public final class DayProgressionDefinitions extends SimplePreparableReloadListener<DayProgressionDefinitions.Snapshot> {
    private static final ResourceLocation DEFAULT_DEFINITION =
            ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "default");
    private static volatile Snapshot current = Snapshot.defaults();
    public static final DayProgressionDefinitions INSTANCE = new DayProgressionDefinitions();

    private DayProgressionDefinitions() {}

    public static void register(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    @Override
    protected Snapshot prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, Resource> resources = manager.listResources(
                "day_progression", id -> id.getPath().endsWith(".json"));
        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            ResourceLocation definitionId = definitionId(entry.getKey());
            if (definitionId == null || !definitionId.equals(DEFAULT_DEFINITION)) continue;
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (!json.has("stages") || !json.get("stages").isJsonArray()) {
                    throw new IllegalArgumentException("missing stages array");
                }
                List<Stage> stages = new ArrayList<>();
                for (JsonElement element : json.getAsJsonArray("stages")) {
                    JsonObject stage = element.getAsJsonObject();
                    stages.add(new Stage(
                            boundedInt(stage, "min_day", 1, 36_500),
                            boundedInt(stage, "max_day", 1, 36_500),
                            boundedDouble(stage, "detection_multiplier", 0.25D, 4.0D),
                            boundedInt(stage, "group_size", 1, 128),
                            optionalBoolean(stage, "can_break_doors", false)));
                }
                stages.sort(Comparator.comparingInt(Stage::minDay));
                validateStages(stages);
                return new Snapshot(stages);
            } catch (Exception exception) {
                AftermathMod.LOGGER.error("Could not load day progression {}: {}",
                        entry.getKey(), exception.getMessage());
                return Snapshot.empty();
            }
        }
        AftermathMod.LOGGER.warn("No {} day progression definition found; keeping defaults.",
                DEFAULT_DEFINITION);
        return Snapshot.empty();
    }

    @Override
    protected void apply(Snapshot prepared, ResourceManager manager, ProfilerFiller profiler) {
        if (prepared.stages().isEmpty()) {
            AftermathMod.LOGGER.error("Day progression reload produced no usable stages; keeping the previous snapshot.");
            return;
        }
        current = prepared;
        AftermathMod.LOGGER.info("Loaded {} apocalypse day stages.", prepared.stages().size());
    }

    public static Snapshot snapshot() {
        return current;
    }

    public static Stage stageForDay(int day) {
        List<Stage> stages = current.stages();
        if (stages.isEmpty()) return new Stage(1, 36_500, 1.0D, 4, false);
        int safeDay = Math.max(1, day);
        for (Stage stage : stages) {
            if (safeDay >= stage.minDay() && safeDay <= stage.maxDay()) return stage;
            if (safeDay < stage.minDay()) return stage;
        }
        return stages.get(stages.size() - 1);
    }

    private static ResourceLocation definitionId(ResourceLocation fileId) {
        if (!fileId.getNamespace().equals(AftermathMod.MOD_ID)) return null;
        String prefix = "day_progression/";
        String path = fileId.getPath();
        if (!path.startsWith(prefix) || !path.endsWith(".json")) return null;
        String name = path.substring(prefix.length(), path.length() - ".json".length());
        return name.isBlank() ? null : ResourceLocation.fromNamespaceAndPath(fileId.getNamespace(), name);
    }

    private static void validateStages(List<Stage> stages) {
        if (stages.isEmpty()) throw new IllegalArgumentException("stages array cannot be empty");
        if (stages.get(0).minDay() != 1) throw new IllegalArgumentException("first stage must start on day 1");
        int previousMax = 0;
        for (Stage stage : stages) {
            if (stage.maxDay() < stage.minDay()) throw new IllegalArgumentException("max_day must be >= min_day");
            if (stage.minDay() != previousMax + 1) {
                throw new IllegalArgumentException("stages must be contiguous and cannot overlap");
            }
            previousMax = stage.maxDay();
        }
    }

    private static int boundedInt(JsonObject json, String key, int min, int max) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) {
            throw new IllegalArgumentException("missing numeric field " + key);
        }
        int value = json.get(key).getAsInt();
        if (value < min || value > max) throw new IllegalArgumentException(key + " outside permitted range");
        return value;
    }

    private static double boundedDouble(JsonObject json, String key, double min, double max) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) {
            throw new IllegalArgumentException("missing numeric field " + key);
        }
        double value = json.get(key).getAsDouble();
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(key + " outside permitted range");
        }
        return value;
    }

    private static boolean optionalBoolean(JsonObject json, String key, boolean fallback) {
        if (!json.has(key)) return fallback;
        if (!json.get(key).isJsonPrimitive()) throw new IllegalArgumentException(key + " must be boolean");
        return json.get(key).getAsBoolean();
    }

    public record Stage(int minDay, int maxDay, double detectionMultiplier, int groupSize,
                        boolean canBreakDoors) {}

    public record Snapshot(List<Stage> stages) {
        public Snapshot {
            stages = List.copyOf(stages);
        }
        public static Snapshot empty() { return new Snapshot(List.of()); }
        public static Snapshot defaults() {
            return new Snapshot(List.of(
                    new Stage(1, 3, 1.0D, 4, false),
                    new Stage(4, 7, 1.15D, 8, false),
                    new Stage(8, 14, 1.3D, 12, false)));
        }
    }
}
