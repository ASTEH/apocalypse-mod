package dev.aftermath.character;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

public record CharacterProfession(
        ResourceLocation id,
        String nameKey,
        String descriptionKey,
        int startingPoints,
        Set<ResourceLocation> allowedTraits
) {
    public CharacterProfession {
        allowedTraits = Set.copyOf(allowedTraits);
    }
}
