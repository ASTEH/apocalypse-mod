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
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Loads character professions and traits from data/aftermath at datapack reload time.
 * Definitions are published as one immutable snapshot after parsing.
 */
public final class CharacterDefinitions extends SimplePreparableReloadListener<CharacterDefinitions.Snapshot> {
    public static final CharacterDefinitions INSTANCE = new CharacterDefinitions();
    private static final Set<String> ATTRIBUTE_TARGETS = Set.of("movement_speed", "max_health", "attack_damage", "attack_speed", "armor");
    private static final Set<String> CUSTOM_HOOKS = Set.of("iron_stomach", "keen_hearing", "light_eater", "deaf", "hard_of_hearing", "short_sighted", "slow_healer", "clumsy_noise", "cowardly", "asthmatic", "eagle_eye", "quick_reload", "speed_demon", "sleepyhead", "illiterate");
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
            if (isValidProfession(profession, traits)) {
                validProfessions.put(profession.id(), profession);
            } else {
                AftermathMod.LOGGER.error("Ignoring profession {} because its allowed/granted traits are missing, incompatible, or invalid for this profession.", profession.id());
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
                if (json.has("id") && !id.toString().equals(requiredString(json, "id"))) {
                    throw new IllegalArgumentException("id field must match profession resource location " + id);
                }
                String name = requiredString(json, "name");
                String description = requiredString(json, "description");
                String pointsKey = json.has("startPoints") ? "startPoints" : "starting_points";
                int points = boundedInt(json, pointsKey, 0, 100);
                String allowedKey = json.has("allowedTraits") ? "allowedTraits" : "allowed_traits";
                List<ResourceLocation> allowedTraits = idList(json, allowedKey);
                ResourceLocation icon = ResourceLocation.tryParse(json.has("icon")
                        ? requiredString(json, "icon") : "minecraft:player_head");
                if (icon == null) throw new IllegalArgumentException("invalid profession icon resource ID");
                List<StartingItem> startingItems = startingItems(json);
                String grantsKey = json.has("grantedTraits") ? "grantedTraits" : "granted_traits";
                List<ResourceLocation> grantedTraits = idList(json, grantsKey);
                Map<String, Integer> skillBonuses = skillBonuses(json);
                result.put(id, new ProfessionDefinition(id, name, description, icon, points,
                        allowedTraits, startingItems, grantedTraits, skillBonuses));
            } catch (Exception exception) {
                AftermathMod.LOGGER.error("Could not load character profession {}: {}", entry.getKey(), exception.getMessage());
            }
        });
        return result;
    }

    private static boolean isValidProfession(ProfessionDefinition profession,
                                             Map<ResourceLocation, TraitDefinition> traits) {
        if (!profession.allowedTraits().isEmpty()
                && profession.allowedTraits().stream().anyMatch(id -> !traits.containsKey(id))) {
            return false;
        }
        Set<ResourceLocation> granted = Set.copyOf(profession.grantedTraits());
        if (granted.size() != profession.grantedTraits().size()) return false;
        for (ResourceLocation id : profession.grantedTraits()) {
            TraitDefinition trait = traits.get(id);
            if (trait == null) return false;
            if (!profession.allowedTraits().isEmpty() && !profession.allowedTraits().contains(id)) return false;
            if (trait.requiresProfession() != null && !trait.requiresProfession().equals(profession.id())) return false;
            if (trait.conflicts().stream().anyMatch(granted::contains)) return false;
        }
        return true;
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
                if (json.has("id") && !id.toString().equals(requiredString(json, "id"))) {
                    throw new IllegalArgumentException("id field must match trait resource location " + id);
                }
                String name = requiredString(json, "name");
                String description = requiredString(json, "description");
                String category = (json.has("type") ? requiredString(json, "type") : requiredString(json, "category")).toLowerCase(Locale.ROOT);
                if (!Set.of("positive", "negative", "neutral").contains(category)) {
                    throw new IllegalArgumentException("type/category must be POSITIVE, NEGATIVE or NEUTRAL");
                }
                int cost = 0;
                int credit = 0;
                if (json.has("pointCost")) {
                    int signedCost = boundedInt(json, "pointCost", -100, 100);
                    if ((category.equals("positive") && signedCost <= 0) || (category.equals("negative") && signedCost >= 0) || (category.equals("neutral") && signedCost != 0)) {
                        throw new IllegalArgumentException("pointCost must be positive for POSITIVE, negative for NEGATIVE, and 0 for NEUTRAL");
                    }
                    if (category.equals("positive")) cost = signedCost;
                    else if (category.equals("negative")) credit = -signedCost;
                } else {
                    cost = category.equals("positive") ? boundedInt(json, "point_cost", 1, 100) : 0;
                    credit = category.equals("negative") ? boundedInt(json, "point_credit", 1, 100) : 0;
                    if ((category.equals("positive") && json.has("point_credit")) || (category.equals("negative") && json.has("point_cost"))) {
                        throw new IllegalArgumentException("legacy trait cannot define both point_cost and point_credit");
                    }
                }
                List<TraitEffect> effects = effects(json);
                List<ResourceLocation> conflicts = json.has("incompatibleWith") ? idList(json, "incompatibleWith") : idList(json, "conflicts");
                ResourceLocation requiresProfession = optionalId(json, "requiresProfession", "requires_profession");
                result.put(id, new TraitDefinition(id, name, description, category, cost, credit, effects, conflicts, requiresProfession));
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

    private static ResourceLocation optionalId(JsonObject json, String primaryKey, String legacyKey) {
        String key = json.has(primaryKey) ? primaryKey : legacyKey;
        if (!json.has(key)) return null;
        ResourceLocation id = ResourceLocation.tryParse(requiredString(json, key));
        if (id == null) throw new IllegalArgumentException("invalid resource id in " + key);
        return id;
    }

    private static List<StartingItem> startingItems(JsonObject json) {
        String key = json.has("startItems") ? "startItems" : "starting_items";
        List<StartingItem> result = new ArrayList<>();
        if (!json.has(key) || !json.get(key).isJsonArray()) return result;
        for (JsonElement element : json.getAsJsonArray(key)) {
            JsonObject item = element.getAsJsonObject();
            String itemKey = item.has("item") ? "item" : "id";
            ResourceLocation id = ResourceLocation.tryParse(requiredString(item, itemKey));
            if (id == null) throw new IllegalArgumentException("invalid item ID in " + key);
            int count = boundedInt(item, "count", 1, 64);
            result.add(new StartingItem(id, count));
        }
        return List.copyOf(result);
    }

    private static Map<String, Integer> skillBonuses(JsonObject json) {
        String key = json.has("skillBonuses") ? "skillBonuses" : "skill_bonuses";
        if (!json.has(key)) return Map.of();
        if (!json.get(key).isJsonObject()) throw new IllegalArgumentException(key + " must be an object of skill IDs to starting levels");
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject(key).entrySet()) {
            String skillId = entry.getKey().toLowerCase(Locale.ROOT);
            if (!skillId.matches("[a-z0-9_.-]+")) throw new IllegalArgumentException("invalid skill ID " + entry.getKey());
            if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("starting skill level must be a number: " + skillId);
            }
            int level = entry.getValue().getAsInt();
            if (level < 0 || level > 10) throw new IllegalArgumentException("starting skill level must be between 0 and 10: " + skillId);
            if (level > 0) result.put(skillId, level);
        }
        return Map.copyOf(result);
    }

    private static List<TraitEffect> effects(JsonObject json) {
        List<TraitEffect> result = new ArrayList<>();
        if (!json.has("effects") || !json.get("effects").isJsonArray()) return result;
        for (JsonElement element : json.getAsJsonArray("effects")) {
            JsonObject effect = element.getAsJsonObject();
            String type = requiredString(effect, "type").toLowerCase(Locale.ROOT);
            if (type.equals("attribute_modifier")) type = "attribute_multiplier";
            if (type.equals("vanilla_mob_effect")) type = "mob_effect";
            String target = effect.has("target") ? requiredString(effect, "target") : "";
            double value = effect.has("value") ? effect.get("value").getAsDouble() : Double.NaN;
            if (!Double.isFinite(value)) throw new IllegalArgumentException("effect value must be finite");
            int durationTicks = effect.has("duration_ticks")
                    ? boundedInt(effect, "duration_ticks", 20, 72_000) : 220;

            switch (type) {
                case "attribute_multiplier" -> {
                    if (!ATTRIBUTE_TARGETS.contains(target)) throw new IllegalArgumentException("unsupported attribute target " + target);
                    if (value < 0.5 || value > 1.5) throw new IllegalArgumentException("attribute multiplier must be between 0.5 and 1.5");
                }
                case "learning_xp_multiplier", "weapon_xp_multiplier", "reading_speed_multiplier" -> {
                    if (value < 0.25 || value > 3.0) throw new IllegalArgumentException(type + " value must be between 0.25 and 3.0");
                }
                case "damage_multiplier" -> {
                    if (!target.equals("hostile") || value < 0.25 || value > 1.0) {
                        throw new IllegalArgumentException("damage_multiplier requires target hostile and value in [0.25, 1.0]");
                    }
                }
                case "mob_effect" -> {
                    if (ResourceLocation.tryParse(target) == null || value < 0 || value > 5 || value != Math.rint(value)) {
                        throw new IllegalArgumentException("mob_effect requires an effect resource ID and integer amplifier 0..5");
                    }
                }
                case "custom_hook" -> {
                    if (!CUSTOM_HOOKS.contains(target) || value < 0 || value > 3.0) {
                        throw new IllegalArgumentException("unsupported custom hook or value: " + target);
                    }
                }
                default -> throw new IllegalArgumentException("unsupported effect type " + type);
            }
            result.add(new TraitEffect(type, target, value, durationTicks));
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
                    json.addProperty("icon", value.icon().toString());
                    json.addProperty("starting_points", value.startingPoints());
                    JsonArray allowed = new JsonArray();
                    value.allowedTraits().forEach(id -> allowed.add(id.toString()));
                    json.add("allowed_traits", allowed);
                    JsonArray granted = new JsonArray();
                    value.grantedTraits().forEach(id -> granted.add(id.toString()));
                    json.add("granted_traits", granted);
                    JsonObject bonuses = new JsonObject();
                    value.skillBonuses().forEach((skill, level) -> bonuses.addProperty(skill, level));
                    json.add("skill_bonuses", bonuses);
                    JsonArray startingItems = new JsonArray();
                    value.startingItems().forEach(startingItem -> {
                        JsonObject item = new JsonObject();
                        item.addProperty("item", startingItem.itemId().toString());
                        item.addProperty("count", startingItem.count());
                        startingItems.add(item);
                    });
                    json.add("starting_items", startingItems);
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
                    json.addProperty("type", value.category().toUpperCase(Locale.ROOT));
                    json.addProperty("point_cost", value.pointCost());
                    json.addProperty("point_credit", value.pointCredit());
                    if (value.requiresProfession() != null) json.addProperty("requires_profession", value.requiresProfession().toString());
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
                                       ResourceLocation icon, int startingPoints,
                                       List<ResourceLocation> allowedTraits, List<StartingItem> startingItems,
                                       List<ResourceLocation> grantedTraits, Map<String, Integer> skillBonuses) {
        public ProfessionDefinition {
            allowedTraits = List.copyOf(allowedTraits);
            startingItems = List.copyOf(startingItems);
            grantedTraits = List.copyOf(grantedTraits);
            skillBonuses = Map.copyOf(skillBonuses);
        }
    }

    public record TraitDefinition(ResourceLocation id, String nameKey, String descriptionKey,
                                  String category, int pointCost, int pointCredit,
                                  List<TraitEffect> effects, List<ResourceLocation> conflicts,
                                  ResourceLocation requiresProfession) {
        public boolean positive() { return category.equals("positive"); }
    }

    public record TraitEffect(String type, String target, double value, int durationTicks) {}
    public record StartingItem(ResourceLocation itemId, int count) {}
}
