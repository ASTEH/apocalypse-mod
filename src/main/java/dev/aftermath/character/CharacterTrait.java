package dev.aftermath.character;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

public record CharacterTrait(
        ResourceLocation id,
        String nameKey,
        String descriptionKey,
        Category category,
        int pointCost,
        int pointCredit,
        Set<ResourceLocation> conflicts
) {
    public enum Category {
        POSITIVE,
        NEGATIVE
    }

    public CharacterTrait {
        conflicts = Set.copyOf(conflicts);
    }

    public int budgetDelta() {
        return category == Category.POSITIVE ? -pointCost : pointCredit;
    }
}
