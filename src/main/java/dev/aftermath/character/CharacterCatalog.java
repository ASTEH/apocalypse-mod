package dev.aftermath.character;

import dev.aftermath.AftermathMod;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** An atomically published snapshot of validated datapack character definitions. */
public final class CharacterCatalog {
    private static volatile Snapshot snapshot = new Snapshot(Map.of(), Map.of());

    private CharacterCatalog() {}

    public static Snapshot snapshot() {
        return snapshot;
    }

    public static void replace(Map<ResourceLocation, CharacterProfession> professions,
                               Map<ResourceLocation, CharacterTrait> traits) {
        snapshot = new Snapshot(Map.copyOf(professions), Map.copyOf(traits));
    }

    public static Optional<ResourceLocation> parseId(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String value = raw.contains(":") ? raw : AftermathMod.MOD_ID + ":" + raw;
        try {
            return Optional.ofNullable(ResourceLocation.tryParse(value));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    public static SelectionResult validateSelection(String professionInput, List<String> traitInputs) {
        Snapshot current = snapshot;
        if (current.professions().isEmpty() || current.traits().isEmpty()) {
            return SelectionResult.failure("definitions_not_loaded", null, null, 0, 0);
        }

        ResourceLocation professionId = parseId(professionInput).orElse(null);
        CharacterProfession profession = professionId == null ? null : current.professions().get(professionId);
        if (profession == null) {
            return SelectionResult.failure("unknown_profession", professionInput, null, 0, 0);
        }

        Set<ResourceLocation> selectedIds = new LinkedHashSet<>();
        List<CharacterTrait> selectedTraits = new ArrayList<>();
        int totalCost = 0;
        int totalCredit = 0;

        for (String traitInput : traitInputs) {
            ResourceLocation traitId = parseId(traitInput).orElse(null);
            if (traitId == null) {
                return SelectionResult.failure("unknown_trait", traitInput, null, 0, 0);
            }
            if (!selectedIds.add(traitId)) {
                return SelectionResult.failure("duplicate_trait", traitId.toString(), null, 0, 0);
            }

            CharacterTrait trait = current.traits().get(traitId);
            if (trait == null) {
                return SelectionResult.failure("unknown_trait", traitInput, null, 0, 0);
            }
            if (!profession.allowedTraits().isEmpty() && !profession.allowedTraits().contains(traitId)) {
                return SelectionResult.failure("trait_not_allowed", traitId.toString(), profession.id().toString(), 0, 0);
            }

            for (ResourceLocation conflict : trait.conflicts()) {
                if (selectedIds.contains(conflict)) {
                    return SelectionResult.failure("trait_conflict", trait.id().toString(), conflict.toString(), 0, 0);
                }
            }
            selectedTraits.add(trait);
            totalCost += trait.pointCost();
            totalCredit += trait.pointCredit();
        }

        int availablePoints = profession.startingPoints() + totalCredit;
        if (totalCost > availablePoints) {
            return SelectionResult.failure("insufficient_points", null, null, totalCost, availablePoints);
        }

        List<String> normalizedTraits = selectedTraits.stream()
                .map(trait -> trait.id().toString())
                .toList();
        CharacterProfile profile = new CharacterProfile(profession.id().toString(), normalizedTraits, true);
        return SelectionResult.success(profile, availablePoints - totalCost);
    }

    public record Snapshot(
            Map<ResourceLocation, CharacterProfession> professions,
            Map<ResourceLocation, CharacterTrait> traits
    ) {
        public Snapshot {
            professions = Map.copyOf(professions);
            traits = Map.copyOf(traits);
        }

        public boolean isLoaded() {
            return !professions.isEmpty() && !traits.isEmpty();
        }
    }

    public record SelectionResult(
            CharacterProfile profile,
            String errorCode,
            String argumentOne,
            String argumentTwo,
            int requiredPoints,
            int availablePoints,
            int remainingPoints
    ) {
        public boolean accepted() {
            return profile != null;
        }

        private static SelectionResult failure(String code, String arg1, String arg2, int required, int available) {
            return new SelectionResult(null, code, arg1, arg2, required, available, 0);
        }

        private static SelectionResult success(CharacterProfile profile, int remaining) {
            return new SelectionResult(profile, null, null, null, 0, 0, remaining);
        }
    }
}
