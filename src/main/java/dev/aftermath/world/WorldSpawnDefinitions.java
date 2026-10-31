package dev.aftermath.world;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aftermath.AftermathMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.io.Reader;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Datapack-driven world spawn policy. It deliberately contains no client references and is safe
 * to load on a dedicated server. Invalid reloads keep the last valid snapshot.
 */
public final class WorldSpawnDefinitions extends SimplePreparableReloadListener<WorldSpawnDefinitions.Snapshot> {
    private static final ResourceLocation RULES_FILE =
            ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "world_rules/spawn_control.json");

    public static final WorldSpawnDefinitions INSTANCE = new WorldSpawnDefinitions();
    private static volatile Snapshot current = Snapshot.empty();

    private WorldSpawnDefinitions() {}

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
            AftermathMod.LOGGER.error("Missing required world spawn policy {}. Keeping the previous policy.", RULES_FILE);
            return current;
        }

        try (Reader reader = resource.openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject hostile = requiredObject(root, "hostile_spawns");
            JsonObject spawners = requiredObject(root, "spawner_removal");

            ResourceLocation allowedTag = parseId(requiredString(hostile, "allow_hostile_entity_tag"));
            Snapshot prepared = new Snapshot(
                    boolOrDefault(hostile, "enabled", true),
                    boolOrDefault(hostile, "deny_unlisted_hostiles", true),
                    idSet(hostile, "dimensions"),
                    allowedTag,
                    namespaceSet(hostile, "allow_entity_namespaces"),
                    idSet(hostile, "blocked_entity_types"),
                    boolOrDefault(spawners, "enabled", true),
                    idSet(spawners, "dimensions"),
                    idSet(spawners, "block_ids"));

            if (prepared.hostileSpawnFilterEnabled()
                    && prepared.allowedHostileTag() == null) {
                throw new IllegalArgumentException("hostile_spawns.allow_hostile_entity_tag is invalid");
            }
            AftermathMod.LOGGER.info(
                    "Loaded world spawn policy: hostileFilter={}, explicitlyBlocked={}, allowedNamespaces={}, spawnerBlocks={}.",
                    prepared.hostileSpawnFilterEnabled(), prepared.blockedEntityTypes().size(),
                    prepared.allowedEntityNamespaces().size(), prepared.spawnerBlockIds().size());
            return prepared;
        } catch (Exception exception) {
            AftermathMod.LOGGER.error("Could not load world spawn policy {}: {}. Keeping the previous policy.",
                    RULES_FILE, exception.getMessage());
            return current;
        }
    }

    @Override
    protected void apply(Snapshot prepared, ResourceManager manager, ProfilerFiller profiler) {
        current = prepared;
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

    private static boolean boolOrDefault(JsonObject json, String key, boolean fallback) {
        if (!json.has(key)) return fallback;
        if (!json.get(key).isJsonPrimitive()
                || !json.get(key).getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(key + " must be a boolean");
        }
        return json.get(key).getAsBoolean();
    }

    private static ResourceLocation parseId(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) throw new IllegalArgumentException("invalid resource location " + value);
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
            result.add(parseId(element.getAsString()));
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
                throw new IllegalArgumentException("invalid entity namespace " + namespace);
            }
            result.add(namespace);
        }
        return Set.copyOf(result);
    }

    public record Snapshot(
            boolean hostileSpawnFilterEnabled,
            boolean denyUnlistedHostiles,
            Set<ResourceLocation> hostileDimensions,
            ResourceLocation allowedHostileTag,
            Set<String> allowedEntityNamespaces,
            Set<ResourceLocation> blockedEntityTypes,
            boolean spawnerRemovalEnabled,
            Set<ResourceLocation> spawnerDimensions,
            Set<ResourceLocation> spawnerBlockIds) {

        public Snapshot {
            hostileDimensions = Set.copyOf(hostileDimensions);
            allowedEntityNamespaces = Set.copyOf(allowedEntityNamespaces);
            blockedEntityTypes = Set.copyOf(blockedEntityTypes);
            spawnerDimensions = Set.copyOf(spawnerDimensions);
            spawnerBlockIds = Set.copyOf(spawnerBlockIds);
        }

        public boolean shouldBlockSpawn(Mob mob, ResourceLocation dimension) {
            if (!hostileSpawnFilterEnabled) return false;
            if (!hostileDimensions.isEmpty() && !hostileDimensions.contains(dimension)) return false;
            ResourceLocation entityId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
            if (entityId != null && blockedEntityTypes.contains(entityId)) return true;
            if (!denyUnlistedHostiles || !(mob instanceof Enemy)) return false;
            if (entityId != null && allowedEntityNamespaces.contains(entityId.getNamespace())) return false;
            if (allowedHostileTag == null) return true;
            TagKey<EntityType<?>> allowTag = TagKey.create(Registries.ENTITY_TYPE, allowedHostileTag);
            return !mob.getType().is(allowTag);
        }

        public boolean shouldRemoveSpawners(ResourceLocation dimension) {
            if (!spawnerRemovalEnabled || spawnerBlockIds.isEmpty()) return false;
            return spawnerDimensions.isEmpty() || spawnerDimensions.contains(dimension);
        }

        public static Snapshot empty() {
            return new Snapshot(false, false, Set.of(), null, Set.of(), Set.of(),
                    false, Set.of(), Set.of());
        }
    }
}
