package dev.aftermath.learning;

import com.google.gson.JsonArray;
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
import java.util.LinkedHashMap;
import java.util.List;
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
        return new Snapshot(
                loadLessons(manager),
                loadPrograms(manager),
                loadSkills(manager),
                loadBooks(manager),
                loadVhsTapes(manager));
    }

    @Override
    protected void apply(Snapshot prepared, ResourceManager manager, ProfilerFiller profiler) {
        current = prepared;
        AftermathMod.LOGGER.info(
                "Loaded learning data: {} lessons, {} skills, {} books, {} TV programs and {} VHS tapes.",
                prepared.lessons().size(), prepared.skills().size(), prepared.books().size(),
                prepared.programs().size(), prepared.vhsTapes().size());
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
                LessonDefinition definition = new LessonDefinition(
                        id,
                        requiredString(json, "title"),
                        parseId(requiredString(json, "skill"), "skill"),
                        parseId(requiredString(json, "item"), "item"),
                        boundedInt(json, "duration_ticks", 20, 20 * 60 * 60),
                        boundedInt(json, "xp_reward", 0, 100_000),
                        boundedInt(json, "max_completions", 1, 100),
                        boolOrDefault(json, "cancel_on_movement", true),
                        boolOrDefault(json, "cancel_on_damage", true));
                result.put(id, definition);
            } catch (Exception exception) {
                AftermathMod.LOGGER.error("Could not load learning lesson {}: {}",
                        entry.getKey(), exception.getMessage());
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
                TvProgramDefinition definition = new TvProgramDefinition(
                        id,
                        requiredString(json, "title"),
                        parseId(requiredString(json, "skill"), "skill"),
                        boundedInt(json, "start_time", 0, 23_999),
                        boundedInt(json, "duration_ticks", 20, 24_000),
                        boundedInt(json, "xp_reward", 0, 100_000),
                        boundedInt(json, "repeat_after_days", 1, 365),
                        boundedInt(json, "max_completions_per_player", 1, 100));
                result.put(id, definition);
            } catch (Exception exception) {
                AftermathMod.LOGGER.error("Could not load television program {}: {}",
                        entry.getKey(), exception.getMessage());
            }
        });
        return Map.copyOf(result);
    }

    private static Map<ResourceLocation, SkillDefinition> loadSkills(ResourceManager manager) {
        Map<ResourceLocation, SkillDefinition> result = new LinkedHashMap<>();
        Map<ResourceLocation, Resource> resources = manager.listResources("skills", id -> id.getPath().endsWith(".json"));
        resources.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            ResourceLocation id = definitionId(entry.getKey(), "skills");
            if (id == null) return;
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                String nameKey = requiredString(json, "name");
                String descriptionKey = stringOrDefault(json, "description", nameKey + ".description");
                String category = stringOrDefault(json, "category", "survival");
                if (!category.matches("[a-z0-9_-]{1,32}")) {
                    throw new IllegalArgumentException("category must be a simple lowercase identifier");
                }

                String progressionSource = stringOrDefault(json, "progression_source", "learning");
                if (!progressionSource.equals("learning") && !progressionSource.equals("weapon")) {
                    throw new IllegalArgumentException("progression_source must be learning or weapon");
                }
                String progressionKey = stringOrDefault(json, "progression_key", id.getPath());
                if (!progressionKey.matches("[a-z0-9_.-]{1,64}")) {
                    throw new IllegalArgumentException("progression_key must be a simple identifier");
                }

                int maxLevel = boundedInt(json, "max_level", 1, 10);
                List<Integer> thresholds;
                if (progressionSource.equals("learning")) {
                    thresholds = integerList(json, "xp_thresholds", maxLevel, 0, 2_000_000_000);
                    if (thresholds.get(0) != 0) {
                        throw new IllegalArgumentException("xp_thresholds must start with 0");
                    }
                    for (int i = 1; i < thresholds.size(); i++) {
                        if (thresholds.get(i) <= thresholds.get(i - 1)) {
                            throw new IllegalArgumentException("xp_thresholds must be strictly increasing");
                        }
                    }
                } else {
                    // Weapon classes keep their authoritative threshold curve in WeaponProgress.
                    // The menu reads that same progression source instead of duplicating its values in JSON.
                    thresholds = List.of();
                }

                result.put(id, new SkillDefinition(id, nameKey, descriptionKey, category,
                        progressionSource, progressionKey, maxLevel, thresholds));
            } catch (Exception exception) {
                AftermathMod.LOGGER.error("Could not load skill definition {}: {}",
                        entry.getKey(), exception.getMessage());
            }
        });
        return Map.copyOf(result);
    }

    private static Map<ResourceLocation, BookDefinition> loadBooks(ResourceManager manager) {
        Map<ResourceLocation, BookDefinition> result = new LinkedHashMap<>();
        Map<ResourceLocation, Resource> resources = manager.listResources("books", id -> id.getPath().endsWith(".json"));
        resources.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            ResourceLocation id = definitionId(entry.getKey(), "books");
            if (id == null) return;
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                double multiplier = requiredNumber(json, "multiplier");
                if (multiplier != 2.0D && multiplier != 3.0D && multiplier != 5.0D) {
                    throw new IllegalArgumentException("book multiplier must be exactly 2, 3 or 5");
                }
                result.put(id, new BookDefinition(
                        id,
                        requiredString(json, "title"),
                        parseId(requiredString(json, "skill"), "skill"),
                        parseId(requiredString(json, "item"), "item"),
                        boundedInt(json, "required_skill_level", 1, 10),
                        multiplier,
                        boundedInt(json, "reading_ticks", 20, 20 * 60 * 60),
                        boundedInt(json, "buff_duration_ticks", 20, 20 * 60 * 60 * 24)));
            } catch (Exception exception) {
                AftermathMod.LOGGER.error("Could not load learning book {}: {}",
                        entry.getKey(), exception.getMessage());
            }
        });
        return Map.copyOf(result);
    }

    private static Map<ResourceLocation, VhsTapeDefinition> loadVhsTapes(ResourceManager manager) {
        Map<ResourceLocation, VhsTapeDefinition> result = new LinkedHashMap<>();
        Map<ResourceLocation, Resource> resources = manager.listResources("vhs_tapes", id -> id.getPath().endsWith(".json"));
        resources.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            ResourceLocation id = definitionId(entry.getKey(), "vhs_tapes");
            if (id == null) return;
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                result.put(id, new VhsTapeDefinition(
                        id,
                        requiredString(json, "title"),
                        parseId(requiredString(json, "program"), "program"),
                        parseId(requiredString(json, "item"), "item"),
                        boolOrDefault(json, "consume_on_use", true)));
            } catch (Exception exception) {
                AftermathMod.LOGGER.error("Could not load VHS tape {}: {}",
                        entry.getKey(), exception.getMessage());
            }
        });
        return Map.copyOf(result);
    }

    private static List<Integer> integerList(JsonObject json, String key, int expectedSize, int min, int max) {
        if (!json.has(key) || !json.get(key).isJsonArray()) {
            throw new IllegalArgumentException(key + " must be an array");
        }
        JsonArray array = json.getAsJsonArray(key);
        if (array.size() != expectedSize) throw new IllegalArgumentException(key + " has an invalid number of entries");
        List<Integer> result = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException(key + " entries must be integers");
            }
            int value = element.getAsInt();
            if (value < min || value > max) throw new IllegalArgumentException(key + " entry outside permitted range");
            result.add(value);
        }
        return List.copyOf(result);
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
        if (id == null) throw new IllegalArgumentException("invalid resource ID in " + field);
        return id;
    }

    private static String requiredString(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()
                || !json.get(key).getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("missing string field " + key);
        }
        return json.get(key).getAsString();
    }

    private static int boundedInt(JsonObject json, String key, int min, int max) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()
                || !json.get(key).getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("missing numeric field " + key);
        }
        int value = json.get(key).getAsInt();
        if (value < min || value > max) throw new IllegalArgumentException(key + " outside permitted range");
        return value;
    }

    private static double requiredNumber(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()
                || !json.get(key).getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("missing numeric field " + key);
        }
        double value = json.get(key).getAsDouble();
        if (!Double.isFinite(value)) throw new IllegalArgumentException(key + " must be finite");
        return value;
    }

    private static String stringOrDefault(JsonObject json, String key, String fallback) {
        if (!json.has(key)) return fallback;
        if (!json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isString()) {
            throw new IllegalArgumentException(key + " must be a string");
        }
        String value = json.get(key).getAsString();
        if (value.isBlank()) throw new IllegalArgumentException(key + " cannot be blank");
        return value;
    }

    private static boolean boolOrDefault(JsonObject json, String key, boolean fallback) {
        return json.has(key) ? json.get(key).getAsBoolean() : fallback;
    }

    public record Snapshot(
            Map<ResourceLocation, LessonDefinition> lessons,
            Map<ResourceLocation, TvProgramDefinition> programs,
            Map<ResourceLocation, SkillDefinition> skills,
            Map<ResourceLocation, BookDefinition> books,
            Map<ResourceLocation, VhsTapeDefinition> vhsTapes) {
        public Snapshot {
            lessons = Map.copyOf(lessons);
            programs = Map.copyOf(programs);
            skills = Map.copyOf(skills);
            books = Map.copyOf(books);
            vhsTapes = Map.copyOf(vhsTapes);
        }
        public static Snapshot empty() {
            return new Snapshot(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
    }

    public record LessonDefinition(ResourceLocation id, String titleKey, ResourceLocation skillId,
                                   ResourceLocation itemId, int durationTicks, int xpReward,
                                   int maxCompletions, boolean cancelOnMovement, boolean cancelOnDamage) {}
    public record TvProgramDefinition(ResourceLocation id, String titleKey, ResourceLocation skillId,
                                      int startTime, int durationTicks, int xpReward,
                                      int repeatAfterDays, int maxCompletions) {}
    public record SkillDefinition(ResourceLocation id, String nameKey, String descriptionKey,
                                  String category, String progressionSource, String progressionKey,
                                  int maxLevel, List<Integer> xpThresholds) {
        public SkillDefinition { xpThresholds = List.copyOf(xpThresholds); }
        public int levelFor(int experience) {
            if (!progressionSource.equals("learning") || xpThresholds.isEmpty()) return 1;
            int level = 1;
            int xp = Math.max(0, experience);
            for (int candidate = 2; candidate <= maxLevel; candidate++) {
                if (xp >= xpThresholds.get(candidate - 1)) level = candidate;
                else break;
            }
            return level;
        }
    }
    public record BookDefinition(ResourceLocation id, String titleKey, ResourceLocation skillId,
                                 ResourceLocation itemId, int requiredSkillLevel,
                                 double multiplier, int readingTicks, int buffDurationTicks) {}
    public record VhsTapeDefinition(ResourceLocation id, String titleKey, ResourceLocation programId,
                                    ResourceLocation itemId, boolean consumeOnUse) {}
}