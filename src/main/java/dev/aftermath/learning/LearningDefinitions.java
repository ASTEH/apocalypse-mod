package dev.aftermath.learning;

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
import java.util.LinkedHashMap;
import java.util.Map;

public final class LearningDefinitions extends SimplePreparableReloadListener<LearningDefinitions.Snapshot> {
    public static final LearningDefinitions INSTANCE = new LearningDefinitions();
    private static volatile Snapshot current = Snapshot.empty();

    private LearningDefinitions() {}

    public static void register(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    @Override
    protected Snapshot prepare(ResourceManager manager, ProfilerFiller profiler) {
        return new Snapshot(loadLessons(manager), loadPrograms(manager));
    }

    @Override
    protected void apply(Snapshot prepared, ResourceManager manager, ProfilerFiller profiler) {
        current = prepared;
        AftermathMod.LOGGER.info("Loaded {} learning lessons and {} television programs.",
                prepared.lessons().size(), prepared.programs().size());
    }

    public static Snapshot snapshot() {
        return current;
    }

    private static Map<ResourceLocation, LessonDefinition> loadLessons(ResourceManager manager) {
        Map<ResourceLocation, LessonDefinition> result = new LinkedHashMap<>();
        Map<ResourceLocation, Resource> resources = manager.listResources("lessons", id -> id.getPath().endsWith(".json"));
        resources.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            ResourceLocation id = definitionId(entry.getKey(), "lessons");
            if (id == null) return;
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                ResourceLocation skillId = parseId(requiredString(json, "skill"), "skill");
                ResourceLocation itemId = parseId(requiredString(json, "item"), "item");
                LessonDefinition definition = new LessonDefinition(
                        id,
                        requiredString(json, "title"),
                        skillId,
                        itemId,
                        boundedInt(json, "duration_ticks", 20, 20 * 60 * 60),
                        boundedInt(json, "xp_reward", 0, 100_000),
                        boundedInt(json, "max_completions", 1, 100),
                        boolOrDefault(json, "cancel_on_movement", true),
                        boolOrDefault(json, "cancel_on_damage", true));
                result.put(id, definition);
            } catch (Exception exception) {
                AftermathMod.LOGGER.error("Could not load learning lesson {}: {}", entry.getKey(), exception.getMessage());
            }
        });
        return Map.copyOf(result);
    }

    private static Map<ResourceLocation, TvProgramDefinition> loadPrograms(ResourceManager manager) {
        Map<ResourceLocation, TvProgramDefinition> result = new LinkedHashMap<>();
        Map<ResourceLocation, Resource> resources = manager.listResources("tv_programs", id -> id.getPath().endsWith(".json"));
        resources.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            ResourceLocation id = definitionId(entry.getKey(), "tv_programs");
            if (id == null) return;
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                ResourceLocation skillId = parseId(requiredString(json, "skill"), "skill");
                TvProgramDefinition definition = new TvProgramDefinition(
                        id,
                        requiredString(json, "title"),
                        skillId,
                        boundedInt(json, "start_time", 0, 23_999),
                        boundedInt(json, "duration_ticks", 20, 20 * 60 * 60),
                        boundedInt(json, "xp_reward", 0, 100_000),
                        boundedInt(json, "repeat_after_days", 1, 365),
                        boundedInt(json, "max_completions_per_player", 1, 100));
                result.put(id, definition);
            } catch (Exception exception) {
                AftermathMod.LOGGER.error("Could not load television program {}: {}", entry.getKey(), exception.getMessage());
            }
        });
        return Map.copyOf(result);
    }

    private static ResourceLocation definitionId(ResourceLocation fileId, String folder) {
        String prefix = folder + "/";
        String path = fileId.getPath();
        if (!path.startsWith(prefix) || !path.endsWith(".json")) return null;
        String relative = path.substring(prefix.length(), path.length() - ".json".length());
        if (relative.isBlank()) return null;
        return ResourceLocation.fromNamespaceAndPath(fileId.getNamespace(), relative);
    }

    private static ResourceLocation parseId(String value, String field) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) throw new IllegalArgumentException("invalid resource id in " + field);
        return id;
    }

    private static String requiredString(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) {
            throw new IllegalArgumentException("missing string field " + key);
        }
        return json.get(key).getAsString();
    }

    private static int boundedInt(JsonObject json, String key, int min, int max) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) {
            throw new IllegalArgumentException("missing numeric field " + key);
        }
        int value = json.get(key).getAsInt();
        if (value < min || value > max) throw new IllegalArgumentException(key + " outside permitted range");
        return value;
    }

    private static boolean boolOrDefault(JsonObject json, String key, boolean fallback) {
        return json.has(key) ? json.get(key).getAsBoolean() : fallback;
    }

    public record Snapshot(Map<ResourceLocation, LessonDefinition> lessons,
                           Map<ResourceLocation, TvProgramDefinition> programs) {
        public Snapshot {
            lessons = Map.copyOf(lessons);
            programs = Map.copyOf(programs);
        }
        public static Snapshot empty() { return new Snapshot(Map.of(), Map.of()); }
    }

    public record LessonDefinition(ResourceLocation id, String titleKey, ResourceLocation skillId,
                                   ResourceLocation itemId, int durationTicks, int xpReward,
                                   int maxCompletions, boolean cancelOnMovement, boolean cancelOnDamage) {}
    public record TvProgramDefinition(ResourceLocation id, String titleKey, ResourceLocation skillId,
                                      int startTime, int durationTicks, int xpReward,
                                      int repeatAfterDays, int maxCompletions) {}
}
