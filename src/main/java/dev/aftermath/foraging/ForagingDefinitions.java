package dev.aftermath.foraging;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aftermath.AftermathMod;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/** Reloadable, validated resource tables for the search mode. */
public final class ForagingDefinitions extends SimplePreparableReloadListener<ForagingDefinitions.Snapshot> {
    public static final ForagingDefinitions INSTANCE = new ForagingDefinitions();
    private static volatile Snapshot current = Snapshot.empty();

    private ForagingDefinitions() {}

    public static void register(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    public static Snapshot snapshot() {
        return current;
    }

    @Override
    protected Snapshot prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, Resource> resources =
                manager.listResources("foraging/categories", id -> id.getPath().endsWith(".json"));
        Map<String, Category> categories = new LinkedHashMap<>();
        resources.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String path = entry.getKey().getPath();
            String prefix = "foraging/categories/";
            if (!path.startsWith(prefix)) return;
            String categoryId = path.substring(prefix.length(), path.length() - 5);
            if (!categoryId.matches("[a-z0-9_-]{1,32}")) return;
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                JsonArray array = requiredArray(json, "entries");
                if (array.isEmpty() || array.size() > 128) {
                    throw new IllegalArgumentException("entries must contain between 1 and 128 items");
                }
                List<Entry> entries = new ArrayList<>();
                for (JsonElement element : array) {
                    if (!element.isJsonObject()) throw new IllegalArgumentException("entry must be an object");
                    JsonObject item = element.getAsJsonObject();
                    ResourceLocation itemId = ResourceLocation.tryParse(requiredString(item, "item"));
                    if (itemId == null) throw new IllegalArgumentException("invalid item ID");
                    int weight = boundedInt(item, "weight", 1, 10_000);
                    int minSkill = intOrDefault(item, "min_skill", 1, 1, 10);
                    int minCount = intOrDefault(item, "min_count", 1, 1, 16);
                    int maxCount = intOrDefault(item, "max_count", minCount, minCount, 16);
                    List<String> biomes = stringList(item, "biomes", 16);
                    entries.add(new Entry(itemId, weight, minSkill, minCount, maxCount, biomes,
                            boolOrDefault(item, "requires_wet", false),
                            boolOrDefault(item, "night_only", false),
                            boolOrDefault(item, "winter_only", false),
                            boolOrDefault(item, "plant", false),
                            boundedIntOrDefault(item, "winter_bonus", 1, 1, 20)));
                }
                categories.put(categoryId, new Category(categoryId, List.copyOf(entries)));
            } catch (Exception exception) {
                AftermathMod.LOGGER.error("Could not load foraging category {}: {}",
                        entry.getKey(), exception.getMessage());
            }
        });
        return new Snapshot(categories);
    }

    @Override
    protected void apply(Snapshot prepared, ResourceManager manager, ProfilerFiller profiler) {
        if (prepared.categories().isEmpty()) {
            AftermathMod.LOGGER.error("Foraging reload found no valid categories; keeping the previous tables.");
            return;
        }
        current = prepared;
        AftermathMod.LOGGER.info("Loaded {} foraging categories and {} loot entries.",
                prepared.categories().size(),
                prepared.categories().values().stream().mapToInt(c -> c.entries().size()).sum());
    }

    /** Selects a stable loot result for a world position; focus affects discovery, not loot identity. */
    public static LootChoice chooseLoot(ServerLevel level, BlockPos pos, int skillLevel,
                                        boolean wet, boolean night, boolean winter, RandomSource random) {
        List<WeightedEntry> candidates = new ArrayList<>();
        int totalWeight = 0;
        String biome = level.getBiome(pos).unwrapKey()
                .map(key -> key.location().getPath().toLowerCase(java.util.Locale.ROOT))
                .orElse("");
        for (Category category : current.categories().values()) {
            for (Entry entry : category.entries()) {
                if (skillLevel < entry.minSkill()) continue;
                if (entry.requiresWet() && !wet) continue;
                if (entry.nightOnly() && !night) continue;
                if (entry.winterOnly() && !winter) continue;
                if (winter && entry.plant() && !entry.winterOnly()) continue;
                if (!entry.biomes().isEmpty()
                        && entry.biomes().stream().noneMatch(biome::contains)) continue;

                int weight = entry.weight();
                if (winter) weight = Math.min(100_000, weight * entry.winterBonus());
                totalWeight += weight;
                candidates.add(new WeightedEntry(category.id(), entry, weight));
            }
        }
        if (totalWeight <= 0 || candidates.isEmpty()) return null;

        int roll = random.nextInt(totalWeight);
        for (WeightedEntry candidate : candidates) {
            roll -= candidate.weight();
            if (roll < 0) {
                Entry entry = candidate.entry();
                int count = entry.minCount() == entry.maxCount()
                        ? entry.minCount()
                        : random.nextInt(entry.minCount(), entry.maxCount() + 1);
                return new LootChoice(candidate.category(), entry.itemId(), count);
            }
        }
        return null;
    }

    public record Snapshot(Map<String, Category> categories) {
        public Snapshot {
            categories = Map.copyOf(categories);
        }
        public static Snapshot empty() {
            return new Snapshot(Map.of());
        }
        public List<String> categoryIds() {
            return categories.keySet().stream().sorted().toList();
        }
    }

    public record Category(String id, List<Entry> entries) {
        public Category { entries = List.copyOf(entries); }
    }

    public record Entry(ResourceLocation itemId, int weight, int minSkill, int minCount, int maxCount,
                        List<String> biomes, boolean requiresWet, boolean nightOnly,
                        boolean winterOnly, boolean plant, int winterBonus) {
        public Entry { biomes = List.copyOf(biomes); }
    }

    public record LootChoice(String category, ResourceLocation itemId, int count) {}
    private record WeightedEntry(String category, Entry entry, int weight) {}

    private static JsonArray requiredArray(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonArray()) {
            throw new IllegalArgumentException("missing array " + key);
        }
        return json.getAsJsonArray(key);
    }

    private static String requiredString(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()
                || !json.getAsJsonPrimitive(key).isString()) {
            throw new IllegalArgumentException("missing string " + key);
        }
        String value = json.get(key).getAsString();
        if (value.isBlank()) throw new IllegalArgumentException(key + " cannot be blank");
        return value;
    }

    private static int boundedInt(JsonObject json, String key, int min, int max) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()
                || !json.getAsJsonPrimitive(key).isNumber()) {
            throw new IllegalArgumentException("missing numeric field " + key);
        }
        int value = json.get(key).getAsInt();
        if (value < min || value > max) throw new IllegalArgumentException(key + " outside permitted range");
        return value;
    }

    private static int intOrDefault(JsonObject json, String key, int fallback, int min, int max) {
        return json.has(key) ? boundedInt(json, key, min, max) : fallback;
    }

    private static int boundedIntOrDefault(JsonObject json, String key, int fallback, int min, int max) {
        return intOrDefault(json, key, fallback, min, max);
    }

    private static boolean boolOrDefault(JsonObject json, String key, boolean fallback) {
        if (!json.has(key)) return fallback;
        if (!json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isBoolean()) {
            throw new IllegalArgumentException(key + " must be boolean");
        }
        return json.get(key).getAsBoolean();
    }

    private static List<String> stringList(JsonObject json, String key, int maxEntries) {
        if (!json.has(key)) return List.of();
        if (!json.get(key).isJsonArray() || json.getAsJsonArray(key).size() > maxEntries) {
            throw new IllegalArgumentException(key + " must be a short string array");
        }
        List<String> result = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray(key)) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(key + " entries must be strings");
            }
            String value = element.getAsString().toLowerCase(java.util.Locale.ROOT);
            if (!value.matches("[a-z0-9_-]{1,48}")) throw new IllegalArgumentException("invalid " + key + " entry");
            result.add(value);
        }
        return List.copyOf(result);
    }
}
