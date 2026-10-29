package dev.aftermath.character;

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
import net.minecraft.world.item.ItemStack;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads character professions and traits from data/aftermath at datapack reload time.
 * Definitions are published as one immutable snapshot after parsing.
 */
public final class CharacterDefinitions extends SimplePreparableReloadListener<CharacterDefinitions.Snapshot> {
    public static final CharacterDefinitions INSTANCE = new CharacterDefinitions();
    private static final Set<String> ATTRIBUTE_TARGETS = Set.of("movement_speed", "max_health", "attack_damage");
    private static volatile Snapshot current = Snapshot.empty();

    private CharacterDefinitions() {}

    public static void register(net.neoforged.neoforge.event.AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    @Override
    protected Snapshot prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, TraitDefinition> traits = loadTraits(manager);
        Map<ResourceLocation, ProfessionDefinition> professions = loadProfessions(manager);

        Map<ResourceLocation, ProfessionDefinition> validProfessions = new LinkedHashMap<>();
        for (ProfessionDefinition profession : professions.values()) {
            if (profession.allowedTraits().stream().allMatch(traits::containsKey)) {
                validProfessions.put(profession.id(), profession);
            } else {
                AftermathMod.LOGGER.error("Ignoring profession {} because one or more allowed traits are missing.", profession.id());
            }
        }
        return new Snapshot(Map.copyOf(validProfessions), Map.copyOf(traits));
    }

    @Override
    protected void apply(Snapshot prepared, ResourceManager manager, ProfilerFiller profiler) {
        if (prepared.professions().isEmpty() || prepared.traits().isEmpty()) {
            AftermathMod.LOGGER.error("Character definitions reload produced no usable professions or traits; keeping the previous snapshot.");
            return;
        }
        current = prepared;
        AftermathMod.LOGGER.info("Loaded {} character professions and {} traits.",
                prepared.professions().size(), prepared.traits().size());
    }

    public static Snapshot snapshot() {
        return current;
    }

    private static Map<ResourceLocation, ProfessionDefinition> loadProfessions(ResourceManager manager) {
        Map<ResourceLocation, ProfessionDefinition> result = new LinkedHashMap<>();
        Map<ResourceLocation, Resource> resources = manager.listResources("professions",
                id -> id.getPath().endsWith(".json"));
        resources.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            ResourceLocation id = definitionId(entry.getKey(), "professions");
            if (id == null) return;
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                String name = requiredString(json, "name");
                String description = requiredString(json, "description");
                int points = boundedInt(json, "starting_points", 0, 100);
                List<ResourceLocation> allowedTraits = idList(json, "allowed_traits");
                List<StartingItem> startingItems = startingItems(json);
                result.put(id, new ProfessionDefinition(id, name, description, points, allowedTraits, startingItems));
            } catch (Exception exception) {
                AftermathMod.LOGGER.error("Could not load character profession {}: {}", entry.getKey(), exception.getMessage());
            }
        });
        return result;
    }

    private static Map<ResourceLocation, TraitDefinition> loadTraits(ResourceManager manager) {
        Map<ResourceLocation, TraitDefinition> result = new LinkedHashMap<>();
        Map<ResourceLocation, Resource> resources = manager.listResources("traits",
                id -> id.getPath().endsWith(".json"));
        resources.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            ResourceLocation id = definitionId(entry.getKey(), "traits");
            if (id == null) return;
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                String name = requiredString(json, "name");
                String description = requiredString(json, "description");
                String category = requiredString(json, "category");
                if (!category.equals("positive") && !category.equals("negative")) {
                    throw new IllegalArgumentException("category must be positive or negative");
                }
                int cost = category.equals("positive") ? boundedInt(json, "point_cost", 1, 100) : 0;
                int credit = category.equals("negative") ? boundedInt(json, "point_credit", 1, 100) : 0;
                if (category.equals("positive") && json.has("point_credit")
                        || category.equals("negative") && json.has("point_cost")) {
                    throw new IllegalArgumentException("trait cannot define both point_cost and point_credit");
                }
                List<TraitEffect> effects = effects(json);
                List<ResourceLocation> conflicts = idList(json, "conflicts");
                result.put(id, new TraitDefinition(id, name, description, category, cost, credit, effects, conflicts));
            } catch (Exception exception) {
                AftermathMod.LOGGER.error("Could not load character trait {}: {}", entry.getKey(), exception.getMessage());
            }
        });
        return result;
    }

    private static ResourceLocation definitionId(ResourceLocation fileId, String folder) {
        String prefix = folder + "/";
        String path = fileId.getPath();
        if (!path.startsWith(prefix)) return null;
        String relative = path.substring(prefix.length(), path.length() - ".json".length());
        if (relative.isBlank()) return null;
        return ResourceLocation.fromNamespaceAndPath(fileId.getNamespace(), relative);
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
        if (value < min || value > max) {
            throw new IllegalArgumentException(key + " must be between " + min + " and " + max);
        }
        return value;
    }

    private static List<ResourceLocation> idList(JsonObject json, String key) {
        List<ResourceLocation> ids = new ArrayList<>();
        if (!json.has(key) || !json.get(key).isJsonArray()) return ids;
        for (JsonElement element : json.getAsJsonArray(key)) {
            ResourceLocation id = ResourceLocation.tryParse(element.getAsString());
            if (id == null) throw new IllegalArgumentException("invalid resource id in " + key);
            ids.add(id);
        }
        return List.copyOf(ids);
    }

    private static List<StartingItem> startingItems(JsonObject json) {
        List<StartingItem> result = new ArrayList<>();
        if (!json.has("starting_items") || !json.get("starting_items").isJsonArray()) return result;
        for (JsonElement element : json.getAsJsonArray("starting_items")) {
            JsonObject item = element.getAsJsonObject();
            ResourceLocation id = ResourceLocation.tryParse(requiredString(item, "item"));
            if (id == null) throw new IllegalArgumentException("invalid item id in starting_items");
            int count = boundedInt(item, "count", 1, 64);
            result.add(new StartingItem(id, count));
        }
        return List.copyOf(result);
    }

    private static List<TraitEffect> effects(JsonObject json) {
        List<TraitEffect> result = new ArrayList<>();
        if (!json.has("effects") || !json.get("effects").isJsonArray()) return result;
        for (JsonElement element : json.getAsJsonArray("effects")) {
            JsonObject effect = element.getAsJsonObject();
            String type = requiredString(effect, "type");
            String target = effect.has("target") ? effect.get("target").getAsString() : "";
            double value = effect.has("value") ? effect.get("value").getAsDouble() : Double.NaN;
            if (!Double.isFinite(value)) throw new IllegalArgumentException("effect value must be finite");
            if (type.equals("attribute_multiplier")) {
                if (!ATTRIBUTE_TARGETS.contains(target)) {
                    throw new IllegalArgumentException("unsupported attribute target " + target);
                }
                if (value < 0.5 || value > 1.5) {
                    throw new IllegalArgumentException("attribute multiplier must be between 0.5 and 1.5");
                }
            } else if (!type.equals("learning_xp_multiplier") && !type.equals("weapon_xp_multiplier")) {
                throw new IllegalArgumentException("unsupported effect type " + type);
            }
            result.add(new TraitEffect(type, target, value));
        }
        return List.copyOf(result);
    }

    public static String toClientJson() {
        Snapshot snapshot = current;
        JsonObject root = new JsonObject();
        JsonArray professions = new JsonArray();
        snapshot.professions().values().stream()
                .sorted(Comparator.comparing(value -> value.id().toString()))
                .forEach(value -> {
                    JsonObject json = new JsonObject();
                    json.addProperty("id", value.id().toString());
                    json.addProperty("name", value.nameKey());
                    json.addProperty("description", value.descriptionKey());
                    json.addProperty("starting_points", value.startingPoints());
                    JsonArray allowed = new JsonArray();
                    value.allowedTraits().forEach(id -> allowed.add(id.toString()));
                    json.add("allowed_traits", allowed);
                    professions.add(json);
                });
        JsonArray traits = new JsonArray();
        snapshot.traits().values().stream()
                .sorted(Comparator.comparing(value -> value.id().toString()))
                .forEach(value -> {
                    JsonObject json = new JsonObject();
                    json.addProperty("id", value.id().toString());
                    json.addProperty("name", value.nameKey());
                    json.addProperty("description", value.descriptionKey());
                    json.addProperty("category", value.category());
                    json.addProperty("point_cost", value.pointCost());
                    json.addProperty("point_credit", value.pointCredit());
                    JsonArray conflicts = new JsonArray();
                    value.conflicts().forEach(id -> conflicts.add(id.toString()));
                    json.add("conflicts", conflicts);
                    traits.add(json);
                });
        root.add("professions", professions);
        root.add("traits", traits);
        return root.toString();
    }

    public record Snapshot(Map<ResourceLocation, ProfessionDefinition> professions,
                           Map<ResourceLocation, TraitDefinition> traits) {
        public Snapshot {
            professions = Map.copyOf(professions);
            traits = Map.copyOf(traits);
        }

        public static Snapshot empty() {
            return new Snapshot(Map.of(), Map.of());
        }
    }

    public record ProfessionDefinition(ResourceLocation id, String nameKey, String descriptionKey,
                                       int startingPoints, List<ResourceLocation> allowedTraits,
                                       List<StartingItem> startingItems) {}

    public record TraitDefinition(ResourceLocation id, String nameKey, String descriptionKey,
                                  String category, int pointCost, int pointCredit,
                                  List<TraitEffect> effects, List<ResourceLocation> conflicts) {
        public boolean positive() { return category.equals("positive"); }
    }

    public record TraitEffect(String type, String target, double value) {}
    public record StartingItem(ResourceLocation itemId, int count) {}
}
