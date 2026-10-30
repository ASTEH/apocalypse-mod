package dev.aftermath.state;

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

/** Loads data-driven Moodles from data/<namespace>/player_states/*.json. */
public final class PlayerStateDefinitions extends SimplePreparableReloadListener<PlayerStateDefinitions.Snapshot> {
    public static final PlayerStateDefinitions INSTANCE = new PlayerStateDefinitions();
    private static volatile Snapshot current = Snapshot.empty();

    private PlayerStateDefinitions() {}

    public static void register(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    public static Snapshot snapshot() {
        return current;
    }

    @Override
    protected Snapshot prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, PlayerStateDefinition> loaded = new LinkedHashMap<>();
        Map<ResourceLocation, Resource> resources = manager.listResources(
                "player_states", id -> id.getPath().endsWith(".json"));

        resources.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            ResourceLocation id = definitionId(entry.getKey());
            if (id == null) return;
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                PlayerStateDefinition definition = parseDefinition(id, json);
                if (loaded.putIfAbsent(id, definition) != null) {
                    throw new IllegalArgumentException("duplicate player state id " + id);
                }
            } catch (Exception exception) {
                AftermathMod.LOGGER.error("Could not load player state {}: {}",
                        entry.getKey(), exception.getMessage());
            }
        });
        return new Snapshot(loaded);
    }

    @Override
    protected void apply(Snapshot prepared, ResourceManager manager, ProfilerFiller profiler) {
        current = prepared;
        AftermathMod.LOGGER.info("Loaded {} player state definitions.", prepared.states().size());
    }

    private static PlayerStateDefinition parseDefinition(ResourceLocation id, JsonObject json) {
        JsonObject value = requiredObject(json, "value");
        double min = requiredNumber(value, "min");
        double max = requiredNumber(value, "max");
        double initial = requiredNumber(value, "initial");
        if (min >= max || initial < min || initial > max) {
            throw new IllegalArgumentException("value min/max/initial range is invalid");
        }

        return new PlayerStateDefinition(
                id,
                requiredString(json, "name_key"),
                requiredString(json, "description_key"),
                parseId(requiredString(json, "icon"), "icon"),
                min,
                max,
                initial,
                boundedInt(json, "update_interval_ticks", 1, 24_000, 20),
                parseSources(json, "sources"),
                parseSources(json, "recovery"),
                parseInteractions(json),
                parseLevels(json));
    }

    private static List<PlayerStateDefinition.Source> parseSources(JsonObject json, String key) {
        List<PlayerStateDefinition.Source> result = new ArrayList<>();
        if (!json.has(key)) return List.of();
        if (!json.get(key).isJsonArray()) throw new IllegalArgumentException(key + " must be an array");

        for (JsonElement element : json.getAsJsonArray(key)) {
            if (!element.isJsonObject()) throw new IllegalArgumentException(key + " entries must be objects");
            JsonObject source = element.getAsJsonObject();
            result.add(new PlayerStateDefinition.Source(
                    requiredString(source, "type"),
                    numberOrDefault(source, "delta_per_second", 0.0D),
                    numberOrDefault(source, "delta_per_event", 0.0D),
                    boundedNumber(source, "radius", 0.0D, 128.0D, 0.0D),
                    boundedInt(source, "max_entities", 1, 32, 1),
                    numberMap(source, "trait_multipliers")));
        }
        return List.copyOf(result);
    }

    private static List<PlayerStateDefinition.Interaction> parseInteractions(JsonObject json) {
        List<PlayerStateDefinition.Interaction> result = new ArrayList<>();
        if (!json.has("interactions")) return List.of();
        if (!json.get("interactions").isJsonArray()) {
            throw new IllegalArgumentException("interactions must be an array");
        }

        for (JsonElement element : json.getAsJsonArray("interactions")) {
            JsonObject interaction = element.getAsJsonObject();
            String operator = stringOrDefault(interaction, "operator", "gte");
            validateOperator(operator);
            double multiplier = requiredNumber(interaction, "multiplier");
            if (multiplier < 0.0D || multiplier > 4.0D) {
                throw new IllegalArgumentException("interaction multiplier must be between 0 and 4");
            }
            result.add(new PlayerStateDefinition.Interaction(
                    requiredString(interaction, "source_state"),
                    operator,
                    requiredNumber(interaction, "threshold"),
                    multiplier));
        }
        return List.copyOf(result);
    }

    private static List<PlayerStateDefinition.Level> parseLevels(JsonObject json) {
        if (!json.has("levels") || !json.get("levels").isJsonArray()
                || json.getAsJsonArray("levels").isEmpty()) {
            throw new IllegalArgumentException("levels must be a non-empty array");
        }

        List<PlayerStateDefinition.Level> result = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("levels")) {
            JsonObject level = element.getAsJsonObject();
            JsonObject when = requiredObject(level, "when");
            String operator = requiredString(when, "operator");
            validateOperator(operator);
            result.add(new PlayerStateDefinition.Level(
                    requiredString(level, "id"),
                    boundedInt(level, "priority", 0, 1000, 0),
                    operator,
                    requiredNumber(when, "value"),
                    requiredString(level, "name_key"),
                    requiredString(level, "description_key"),
                    parseId(stringOrDefault(level, "icon", requiredString(json, "icon")), "level icon"),
                    numberMap(level, "effects")));
        }
        return List.copyOf(result);
    }

    private static Map<String, Double> numberMap(JsonObject json, String key) {
        if (!json.has(key)) return Map.of();
        if (!json.get(key).isJsonObject()) throw new IllegalArgumentException(key + " must be an object");
        Map<String, Double> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject(key).entrySet()) {
            if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException(key + " values must be numeric");
            }
            double value = entry.getValue().getAsDouble();
            if (!Double.isFinite(value)) throw new IllegalArgumentException(key + " values must be finite");
            result.put(entry.getKey(), value);
        }
        return Map.copyOf(result);
    }

    private static ResourceLocation definitionId(ResourceLocation fileId) {
        String prefix = "player_states/";
        String path = fileId.getPath();
        if (!path.startsWith(prefix) || !path.endsWith(".json")) return null;
        String relative = path.substring(prefix.length(), path.length() - 5);
        return relative.isBlank() ? null : ResourceLocation.fromNamespaceAndPath(fileId.getNamespace(), relative);
    }

    private static ResourceLocation parseId(String value, String field) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) throw new IllegalArgumentException("invalid resource location for " + field);
        return id;
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
        return json.has(key) ? requiredString(json, key) : fallback;
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

    private static double numberOrDefault(JsonObject json, String key, double fallback) {
        return json.has(key) ? requiredNumber(json, key) : fallback;
    }

    private static double boundedNumber(JsonObject json, String key, double min, double max, double fallback) {
        double value = numberOrDefault(json, key, fallback);
        if (value < min || value > max) throw new IllegalArgumentException(key + " outside permitted range");
        return value;
    }

    private static int boundedInt(JsonObject json, String key, int min, int max, int fallback) {
        int value = json.has(key) ? (int) requiredNumber(json, key) : fallback;
        if (value < min || value > max || (json.has(key) && value != requiredNumber(json, key))) {
            throw new IllegalArgumentException(key + " outside permitted integer range");
        }
        return value;
    }

    private static void validateOperator(String operator) {
        if (!List.of("gte", "gt", "lte", "lt", "eq").contains(operator)) {
            throw new IllegalArgumentException("unsupported comparison operator " + operator);
        }
    }

    public record Snapshot(Map<ResourceLocation, PlayerStateDefinition> states) {
        public Snapshot {
            states = Map.copyOf(states);
        }

        public static Snapshot empty() {
            return new Snapshot(Map.of());
        }
    }
}
