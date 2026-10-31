package dev.aftermath.zombie;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Loads weighted zombie archetypes from data/<namespace>/zombie_variants/*.json. */
public final class ZombieVariantDefinitions extends SimplePreparableReloadListener<ZombieVariantDefinitions.Snapshot> {
    public static final ZombieVariantDefinitions INSTANCE = new ZombieVariantDefinitions();
    private static volatile Snapshot current = Snapshot.empty();

    private ZombieVariantDefinitions() {}

    public static void register(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    public static Snapshot snapshot() {
        return current;
    }

    @Override
    protected Snapshot prepare(ResourceManager manager, ProfilerFiller profiler) {
        List<Variant> variants = new ArrayList<>();
        manager.listResources("zombie_variants", id -> id.getPath().endsWith(".json"))
                .entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                    ResourceLocation id = definitionId(entry.getKey());
                    if (id == null) return;
                    try (Reader reader = entry.getValue().openAsReader()) {
                        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                        variants.add(parseVariant(id, json));
                    } catch (Exception exception) {
                        AftermathMod.LOGGER.error("Could not load zombie variant {}: {}",
                                entry.getKey(), exception.getMessage());
                    }
                });
        variants.sort(Comparator.comparing(variant -> variant.id().toString()));
        return new Snapshot(variants);
    }

    @Override
    protected void apply(Snapshot prepared, ResourceManager manager, ProfilerFiller profiler) {
        current = prepared;
        AftermathMod.LOGGER.info("Loaded {} zombie variant definitions.", prepared.variants().size());
    }

    private static Variant parseVariant(ResourceLocation id, JsonObject json) {
        ResourceLocation targetTag = parseId(requiredString(json, "target_entity_tag"), "target_entity_tag");
        int weight = boundedInt(json, "weight", 1, 10000);
        JsonObject spawn = requiredObject(json, "spawn");
        String timeOfDay = stringOrDefault(spawn, "time_of_day", "any").toLowerCase(Locale.ROOT);
        if (!Set.of("any", "day", "night").contains(timeOfDay)) {
            throw new IllegalArgumentException("spawn.time_of_day must be any, day, or night");
        }

        Set<ResourceLocation> dimensions = idSet(spawn, "dimensions");
        Set<ResourceLocation> biomes = idSet(spawn, "biomes");
        Set<String> spawnTypes = stringSet(spawn, "spawn_types", Set.of("natural", "chunk_generation"));
        int minLight = boundedIntOrDefault(spawn, "min_light", 0, 0, 15);
        int maxLight = boundedIntOrDefault(spawn, "max_light", 15, 0, 15);
        if (minLight > maxLight) throw new IllegalArgumentException("spawn.min_light must be <= max_light");

        JsonObject attributes = requiredObject(json, "attributes");
        Stats stats = new Stats(
                requiredRange(attributes, "max_health", 1.0D, 2048.0D),
                requiredRange(attributes, "movement_speed", 0.0D, 2.0D),
                requiredRange(attributes, "attack_damage", 0.0D, 2048.0D),
                rangeOrDefault(attributes, "follow_range", new ValueRange(16.0D, 32.0D), 1.0D, 128.0D),
                rangeOrDefault(attributes, "knockback_resistance", new ValueRange(0.0D, 0.0D), 0.0D, 1.0D));

        JsonObject senses = json.has("senses") ? requiredObject(json, "senses") : new JsonObject();
        ValueRange vision = rangeOrDefault(senses, "vision_range", stats.followRange(), 1.0D, 128.0D);
        ValueRange hearing = rangeOrDefault(senses, "hearing_range", new ValueRange(32.0D, 32.0D), 0.0D, 128.0D);

        return new Variant(id, targetTag, weight,
                new SpawnRules(timeOfDay, dimensions, biomes, spawnTypes, minLight, maxLight),
                stats, new Senses(vision, hearing));
    }

    private static ResourceLocation definitionId(ResourceLocation fileId) {
        String prefix = "zombie_variants/";
        String path = fileId.getPath();
        if (!path.startsWith(prefix) || !path.endsWith(".json")) return null;
        String name = path.substring(prefix.length(), path.length() - ".json".length());
        return name.isBlank() ? null : ResourceLocation.fromNamespaceAndPath(fileId.getNamespace(), name);
    }

    private static JsonObject requiredObject(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonObject()) {
            throw new IllegalArgumentException("missing object field " + key);
        }
        return json.getAsJsonObject(key);
    }

    private static String requiredString(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()
                || !json.get(key).getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("missing string field " + key);
        }
        return json.get(key).getAsString();
    }

    private static String stringOrDefault(JsonObject json, String key, String fallback) {
        if (!json.has(key)) return fallback;
        if (!json.get(key).isJsonPrimitive() || !json.get(key).getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(key + " must be a string");
        }
        return json.get(key).getAsString();
    }

    private static ResourceLocation parseId(String raw, String field) {
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) throw new IllegalArgumentException("invalid resource id in " + field);
        return id;
    }

    private static Set<ResourceLocation> idSet(JsonObject json, String key) {
        if (!json.has(key)) return Set.of();
        if (!json.get(key).isJsonArray()) throw new IllegalArgumentException(key + " must be an array");
        Set<ResourceLocation> result = new LinkedHashSet<>();
        for (JsonElement element : json.getAsJsonArray(key)) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(key + " entries must be strings");
            }
            result.add(parseId(element.getAsString(), key));
        }
        return Set.copyOf(result);
    }

    private static Set<String> stringSet(JsonObject json, String key, Set<String> fallback) {
        if (!json.has(key)) return Set.copyOf(fallback);
        if (!json.get(key).isJsonArray()) throw new IllegalArgumentException(key + " must be an array");
        Set<String> result = new LinkedHashSet<>();
        for (JsonElement element : json.getAsJsonArray(key)) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(key + " entries must be strings");
            }
            String value = element.getAsString().toLowerCase(Locale.ROOT);
            if (!value.matches("[a-z_]+")) throw new IllegalArgumentException("invalid " + key + " entry " + value);
            result.add(value);
        }
        return Set.copyOf(result);
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

    private static int boundedIntOrDefault(JsonObject json, String key, int fallback, int min, int max) {
        return json.has(key) ? boundedInt(json, key, min, max) : fallback;
    }

    private static ValueRange requiredRange(JsonObject json, String key, double min, double max) {
        if (!json.has(key)) throw new IllegalArgumentException("missing numeric field " + key);
        return parseRange(json.get(key), key, min, max);
    }

    private static ValueRange rangeOrDefault(JsonObject json, String key, ValueRange fallback,
                                             double min, double max) {
        return json.has(key) ? parseRange(json.get(key), key, min, max) : fallback;
    }

    private static ValueRange parseRange(JsonElement element, String field, double minAllowed, double maxAllowed) {
        double min;
        double max;
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            min = element.getAsDouble();
            max = min;
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            min = requiredNumber(object, "min");
            max = requiredNumber(object, "max");
        } else {
            throw new IllegalArgumentException(field + " must be a number or {min,max} range");
        }
        if (!Double.isFinite(min) || !Double.isFinite(max)
                || min < minAllowed || max > maxAllowed || min > max) {
            throw new IllegalArgumentException(field + " has an invalid range");
        }
        return new ValueRange(min, max);
    }

    private static double requiredNumber(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()
                || !json.getAsJsonPrimitive(key).isNumber()) {
            throw new IllegalArgumentException("missing numeric field " + key);
        }
        return json.get(key).getAsDouble();
    }

    public record Snapshot(List<Variant> variants) {
        public Snapshot {
            variants = List.copyOf(variants);
        }
        public static Snapshot empty() {
            return new Snapshot(List.of());
        }
    }

    public record Variant(ResourceLocation id, ResourceLocation targetTag, int weight,
                          SpawnRules spawn, Stats stats, Senses senses) {}

    public record SpawnRules(String timeOfDay, Set<ResourceLocation> dimensions,
                             Set<ResourceLocation> biomes, Set<String> spawnTypes,
                             int minLight, int maxLight) {
        public SpawnRules {
            dimensions = Set.copyOf(dimensions);
            biomes = Set.copyOf(biomes);
            spawnTypes = Set.copyOf(spawnTypes);
        }
    }

    public record Stats(ValueRange maxHealth, ValueRange movementSpeed, ValueRange attackDamage,
                        ValueRange followRange, ValueRange knockbackResistance) {}

    public record Senses(ValueRange visionRange, ValueRange hearingRange) {}

    public record ValueRange(double min, double max) {}
}
