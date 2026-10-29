package dev.aftermath.character;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import dev.aftermath.AftermathMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.io.IOException;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Reads only data/<namespace>/professions and data/<namespace>/traits.
 * A bad reload does not replace the last known-good catalog.
 */
public final class CharacterDefinitionReloadListener extends SimplePreparableReloadListener<CharacterDefinitionReloadListener.ParsedDefinitions> {
    private static final Gson GSON = new GsonBuilder().create();

    public static void register(AddReloadListenerEvent event) {
        event.addListener(new CharacterDefinitionReloadListener());
    }

    @Override
    protected ParsedDefinitions prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, CharacterProfession> professions = new LinkedHashMap<>();
        Map<ResourceLocation, CharacterTrait> traits = new LinkedHashMap<>();
        boolean invalid = false;

        for (Map.Entry<ResourceLocation, Resource> entry : resourceManager.listResources(
                "professions", id -> id.getPath().endsWith(".json")).entrySet()) {
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonElement json = GSON.fromJson(reader, JsonElement.class);
                ResourceLocation id = definitionId(entry.getKey(), "professions/");
                CharacterProfession profession = parseProfession(id, json);
                professions.put(profession.id(), profession);
            } catch (IOException | RuntimeException exception) {
                invalid = true;
                AftermathMod.LOGGER.error("Invalid Aftermath profession definition '{}': {}",
                        entry.getKey(), exception.getMessage());
            }
        }

        for (Map.Entry<ResourceLocation, Resource> entry : resourceManager.listResources(
                "traits", id -> id.getPath().endsWith(".json")).entrySet()) {
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonElement json = GSON.fromJson(reader, JsonElement.class);
                ResourceLocation id = definitionId(entry.getKey(), "traits/");
                CharacterTrait trait = parseTrait(id, json);
                traits.put(trait.id(), trait);
            } catch (IOException | RuntimeException exception) {
                invalid = true;
                AftermathMod.LOGGER.error("Invalid Aftermath trait definition '{}': {}",
                        entry.getKey(), exception.getMessage());
            }
        }

        if (professions.isEmpty() || traits.isEmpty()) {
            invalid = true;
            AftermathMod.LOGGER.error("Aftermath character reload found no professions or no traits.");
        }

        for (CharacterProfession profession : professions.values()) {
            for (ResourceLocation traitId : profession.allowedTraits()) {
                if (!traits.containsKey(traitId)) {
                    invalid = true;
                    AftermathMod.LOGGER.error("Profession '{}' refers to missing trait '{}'.", profession.id(), traitId);
                }
            }
        }

        for (CharacterTrait trait : traits.values()) {
            for (ResourceLocation conflict : trait.conflicts()) {
                if (trait.id().equals(conflict) || !traits.containsKey(conflict)) {
                    invalid = true;
                    AftermathMod.LOGGER.error("Trait '{}' has invalid conflict '{}'.", trait.id(), conflict);
                }
            }
        }

        return new ParsedDefinitions(Map.copyOf(professions), Map.copyOf(traits), invalid);
    }

    @Override
    protected void apply(ParsedDefinitions prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
        if (prepared.invalid()) {
            AftermathMod.LOGGER.warn("Rejected Aftermath character definitions reload; the last valid snapshot remains active.");
            return;
        }

        CharacterCatalog.replace(prepared.professions(), prepared.traits());
        AftermathMod.LOGGER.info("Loaded {} professions and {} character traits.",
                prepared.professions().size(), prepared.traits().size());
    }

    private static CharacterProfession parseProfession(ResourceLocation id, JsonElement element) {
        JsonObject json = GsonHelper.convertToJsonObject(element, "profession");
        String name = requiredString(json, "name");
        String description = requiredString(json, "description");
        int points = GsonHelper.getAsInt(json, "starting_points");
        if (points < 0 || points > 100) {
            throw new JsonParseException("'starting_points' must be between 0 and 100.");
        }
        Set<ResourceLocation> allowedTraits = readIds(
                GsonHelper.getAsJsonArray(json, "allowed_traits"), "allowed_traits");
        return new CharacterProfession(id, name, description, points, allowedTraits);
    }

    private static CharacterTrait parseTrait(ResourceLocation id, JsonElement element) {
        JsonObject json = GsonHelper.convertToJsonObject(element, "trait");
        String name = requiredString(json, "name");
        String description = requiredString(json, "description");
        String categoryText = requiredString(json, "category");
        CharacterTrait.Category category = switch (categoryText) {
            case "positive" -> CharacterTrait.Category.POSITIVE;
            case "negative" -> CharacterTrait.Category.NEGATIVE;
            default -> throw new JsonParseException("'category' must be 'positive' or 'negative'.");
        };

        int pointCost = GsonHelper.getAsInt(json, "point_cost", 0);
        int pointCredit = GsonHelper.getAsInt(json, "point_credit", 0);
        if (category == CharacterTrait.Category.POSITIVE) {
            if (pointCost < 1 || pointCost > 100 || pointCredit != 0) {
                throw new JsonParseException("Positive traits require point_cost 1..100 and must not define point_credit.");
            }
        } else if (pointCredit < 1 || pointCredit > 100 || pointCost != 0) {
            throw new JsonParseException("Negative traits require point_credit 1..100 and must not define point_cost.");
        }

        Set<ResourceLocation> conflicts = json.has("conflicts")
                ? readIds(GsonHelper.getAsJsonArray(json, "conflicts"), "conflicts")
                : Set.of();
        return new CharacterTrait(id, name, description, category, pointCost, pointCredit, conflicts);
    }

    private static ResourceLocation definitionId(ResourceLocation fileId, String folderPrefix) {
        String path = fileId.getPath();
        String filePrefix = folderPrefix;
        if (!path.startsWith(filePrefix) || !path.endsWith(".json")) {
            throw new JsonParseException("Unexpected definition path: " + path);
        }
        String shortPath = path.substring(filePrefix.length(), path.length() - ".json".length());
        if (shortPath.isBlank()) {
            throw new JsonParseException("Definition file has an empty id.");
        }
        return ResourceLocation.fromNamespaceAndPath(fileId.getNamespace(), shortPath);
    }

    private static String requiredString(JsonObject json, String key) {
        String value = GsonHelper.getAsString(json, key).trim();
        if (value.isEmpty()) {
            throw new JsonParseException("'" + key + "' must not be empty.");
        }
        return value;
    }

    private static Set<ResourceLocation> readIds(JsonArray values, String fieldName) {
        Set<ResourceLocation> result = new LinkedHashSet<>();
        for (JsonElement value : values) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new JsonParseException("'" + fieldName + "' entries must be resource location strings.");
            }
            ResourceLocation id = ResourceLocation.tryParse(value.getAsString());
            if (id == null) {
                throw new JsonParseException("Invalid resource location in '" + fieldName + "': " + value.getAsString());
            }
            if (!result.add(id)) {
                throw new JsonParseException("Duplicate id in '" + fieldName + "': " + id);
            }
        }
        return Set.copyOf(result);
    }

    public record ParsedDefinitions(
            Map<ResourceLocation, CharacterProfession> professions,
            Map<ResourceLocation, CharacterTrait> traits,
            boolean invalid
    ) {}
}
