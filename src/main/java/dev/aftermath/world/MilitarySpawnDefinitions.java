package dev.aftermath.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aftermath.AftermathMod;
import dev.aftermath.noise.ZombieEntityClassifier;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Mob;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.io.Reader;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Datapack-driven filtering policy for military NPC spawn attempts.
 *
 * Target matching is deliberately opt-in by exact entity ID, namespace, or path keyword.
 * Zombie-like mobs are excluded even if their IDs contain military-related words.
 */
public final class MilitarySpawnDefinitions extends SimplePreparableReloadListener<MilitarySpawnDefinitions.Snapshot> {
    private static final ResourceLocation RULES_FILE =
            ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "world_rules/military_spawn_control.json");

    public static final MilitarySpawnDefinitions INSTANCE = new MilitarySpawnDefinitions();
    private static volatile Snapshot current = Snapshot.empty();

    private MilitarySpawnDefinitions() {}

    public static void register(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    public static Snapshot snapshot() {
        return current;
    }

    @Override
    protected Snapshot prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, Resource> resources =
                manager.listResources("world_rules", id -> id.equals(RULES_FILE));
        Resource resource = resources.get(RULES_FILE);
        if (resource == null) {
            AftermathMod.LOGGER.error("Missing military spawn policy {}. Keeping the previous policy.", RULES_FILE);
            return current;
        }

        try (Reader reader = resource.openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            boolean enabled = boolOrDefault(root, "enabled", true);
            Set<ResourceLocation> dimensions = idSet(root, "dimensions");
            Set<ResourceLocation> entityTypes = idSet(root, "entity_types");
            Set<String> namespaces = namespaceSet(root, "entity_namespaces");
            Set<String> pathKeywords = stringSet(root, "path_keywords");
            Set<String> spawnTypes = stringSet(root, "spawn_types");
            double defaultChance = chance(root, "default_spawn_chance", 0.08D);
            double outsideZoneChance = chance(root, "outside_zone_spawn_chance", 0.01D);
            List<SpawnZone> zones = parseZones(root);

            if (entityTypes.isEmpty() && namespaces.isEmpty() && pathKeywords.isEmpty()) {
                AftermathMod.LOGGER.warn("Military spawn policy has no entity selectors. It will not affect any mobs.");
            }

            Snapshot prepared = new Snapshot(enabled, dimensions, entityTypes, namespaces, pathKeywords,
                    spawnTypes, defaultChance, outsideZoneChance, zones);
            AftermathMod.LOGGER.info(
                    "Loaded military spawn policy: enabled={}, exactEntityIds={}, namespaces={}, pathKeywords={}, spawnTypes={}, zones={}, defaultChance={}, outsideZoneChance={}.",
                    prepared.enabled(), prepared.entityTypes().size(), prepared.entityNamespaces().size(),
                    prepared.pathKeywords().size(), prepared.spawnTypes().size(), prepared.zones().size(),
                    prepared.defaultSpawnChance(), prepared.outsideZoneSpawnChance());
            return prepared;
        } catch (Exception exception) {
            AftermathMod.LOGGER.error("Could not load military spawn policy {}: {}. Keeping the previous policy.",
                    RULES_FILE, exception.getMessage());
            return current;
        }
    }

    @Override
    protected void apply(Snapshot prepared, ResourceManager manager, ProfilerFiller profiler) {
        current = prepared;
    }

    private static boolean boolOrDefault(JsonObject json, String key, boolean fallback) {
        if (!json.has(key)) return fallback;
        if (!json.get(key).isJsonPrimitive()
                || !json.get(key).getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(key + " must be a boolean");
        }
        return json.get(key).getAsBoolean();
    }

    private static Set<ResourceLocation> idSet(JsonObject json, String key) {
        if (!json.has(key)) return Set.of();
        if (!json.get(key).isJsonArray()) throw new IllegalArgumentException(key + " must be an array");
        Set<ResourceLocation> result = new LinkedHashSet<>();
        for (JsonElement element : json.getAsJsonArray(key)) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(key + " entries must be strings");
            }
            ResourceLocation id = ResourceLocation.tryParse(element.getAsString());
            if (id == null) throw new IllegalArgumentException("invalid resource location in " + key);
            result.add(id);
        }
        return Set.copyOf(result);
    }

    private static Set<String> namespaceSet(JsonObject json, String key) {
        if (!json.has(key)) return Set.of();
        if (!json.get(key).isJsonArray()) throw new IllegalArgumentException(key + " must be an array");
        Set<String> result = new LinkedHashSet<>();
        for (JsonElement element : json.getAsJsonArray(key)) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(key + " entries must be strings");
            }
            String namespace = element.getAsString().toLowerCase(Locale.ROOT);
            if (!namespace.matches("[a-z0-9_.-]+")) {
                throw new IllegalArgumentException("invalid namespace " + namespace);
            }
            result.add(namespace);
        }
        return Set.copyOf(result);
    }

    private static Set<String> stringSet(JsonObject json, String key) {
        if (!json.has(key)) return Set.of();
        if (!json.get(key).isJsonArray()) throw new IllegalArgumentException(key + " must be an array");
        Set<String> result = new LinkedHashSet<>();
        for (JsonElement element : json.getAsJsonArray(key)) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(key + " entries must be strings");
            }
            String value = element.getAsString().toLowerCase(Locale.ROOT).trim();
            if (value.isEmpty() || !value.matches("[a-z0-9_]+")) {
                throw new IllegalArgumentException("invalid " + key + " entry " + value);
            }
            result.add(value);
        }
        return Set.copyOf(result);
    }

    private static double chance(JsonObject json, String key, double fallback) {
        if (!json.has(key)) return fallback;
        if (!json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isNumber()) {
            throw new IllegalArgumentException(key + " must be a number");
        }
        double value = json.get(key).getAsDouble();
        if (!Double.isFinite(value) || value < 0.0D || value > 1.0D) {
            throw new IllegalArgumentException(key + " must be between 0.0 and 1.0");
        }
        return value;
    }

    private static List<SpawnZone> parseZones(JsonObject root) {
        if (!root.has("zones")) return List.of();
        if (!root.get("zones").isJsonArray()) throw new IllegalArgumentException("zones must be an array");

        JsonArray array = root.getAsJsonArray("zones");
        List<SpawnZone> zones = new java.util.ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            JsonElement element = array.get(index);
            if (!element.isJsonObject()) throw new IllegalArgumentException("zones[" + index + "] must be an object");
            JsonObject zone = element.getAsJsonObject();

            String name = stringOrDefault(zone, "name", "zone-" + index);
            String rawDimension = stringOrDefault(zone, "dimension", "minecraft:overworld");
            ResourceLocation dimension = ResourceLocation.tryParse(rawDimension);
            if (dimension == null) throw new IllegalArgumentException("invalid dimension in zones[" + index + "]");

            int x = integer(zone, "x");
            int z = integer(zone, "z");
            int radius = boundedInteger(zone, "radius", 1, 4096);
            int minY = zone.has("min_y") ? boundedInteger(zone, "min_y", -2048, 2048) : -2048;
            int maxY = zone.has("max_y") ? boundedInteger(zone, "max_y", -2048, 2048) : 2048;
            if (minY > maxY) throw new IllegalArgumentException("zones[" + index + "] min_y exceeds max_y");
            double spawnChance = chance(zone, "spawn_chance", 1.0D);
            zones.add(new SpawnZone(name, dimension, x, z, radius, minY, maxY, spawnChance));
        }
        return List.copyOf(zones);
    }

    private static String stringOrDefault(JsonObject json, String key, String fallback) {
        if (!json.has(key)) return fallback;
        if (!json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isString()) {
            throw new IllegalArgumentException(key + " must be a string");
        }
        return json.get(key).getAsString();
    }

    private static int integer(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()
                || !json.getAsJsonPrimitive(key).isNumber()) {
            throw new IllegalArgumentException("zones entry is missing numeric field " + key);
        }
        return json.get(key).getAsInt();
    }

    private static int boundedInteger(JsonObject json, String key, int min, int max) {
        int value = integer(json, key);
        if (value < min || value > max) {
            throw new IllegalArgumentException(key + " outside permitted range");
        }
        return value;
    }

    public record SpawnZone(String name, ResourceLocation dimension, int x, int z,
                            int radius, int minY, int maxY, double spawnChance) {
        public boolean contains(ResourceLocation currentDimension, BlockPos pos) {
            if (!dimension.equals(currentDimension) || pos.getY() < minY || pos.getY() > maxY) return false;
            double dx = pos.getX() + 0.5D - x;
            double dz = pos.getZ() + 0.5D - z;
            return dx * dx + dz * dz <= (double) radius * radius;
        }
    }

    public record Snapshot(boolean enabled,
                           Set<ResourceLocation> dimensions,
                           Set<ResourceLocation> entityTypes,
                           Set<String> entityNamespaces,
                           Set<String> pathKeywords,
                           Set<String> spawnTypes,
                           double defaultSpawnChance,
                           double outsideZoneSpawnChance,
                           List<SpawnZone> zones) {
        public Snapshot {
            dimensions = Set.copyOf(dimensions);
            entityTypes = Set.copyOf(entityTypes);
            entityNamespaces = Set.copyOf(entityNamespaces);
            pathKeywords = Set.copyOf(pathKeywords);
            spawnTypes = Set.copyOf(spawnTypes);
            zones = List.copyOf(zones);
        }

        public boolean shouldLimit(Mob mob, ResourceLocation dimension, String spawnType) {
            if (!enabled) return false;
            if (!dimensions.isEmpty() && !dimensions.contains(dimension)) return false;
            if (!spawnTypes.isEmpty() && !spawnTypes.contains(spawnType.toLowerCase(Locale.ROOT))) return false;

            // Never apply military ID heuristics to zombie variants, including modded zombies.
            if (ZombieEntityClassifier.isZombieLikeKillTarget(mob)) return false;

            ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
            if (entityId == null) return false;
            if (entityTypes.contains(entityId) || entityNamespaces.contains(entityId.getNamespace())) return true;

            String normalizedPath = "_" + entityId.getPath().toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9]+", "_") + "_";
            for (String keyword : pathKeywords) {
                if (normalizedPath.contains("_" + keyword + "_")) return true;
            }
            return false;
        }

        public double spawnChance(ResourceLocation dimension, BlockPos pos) {
            if (zones.isEmpty()) return defaultSpawnChance;
            double bestMatchingChance = -1.0D;
            for (SpawnZone zone : zones) {
                if (zone.contains(dimension, pos)) {
                    bestMatchingChance = Math.max(bestMatchingChance, zone.spawnChance());
                }
            }
            return bestMatchingChance >= 0.0D ? bestMatchingChance : outsideZoneSpawnChance;
        }

        public static Snapshot empty() {
            return new Snapshot(false, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
                    1.0D, 1.0D, List.of());
        }
    }
}
