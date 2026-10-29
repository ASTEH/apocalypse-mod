package dev.aftermath.client;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Holds only the selected trait IDs received from the logical server. */
public final class TraitClientState {
    private static volatile Set<String> selectedTraits = Set.of();

    public static void setSelectedTraits(String csv) {
        if (csv == null || csv.isBlank()) {
            selectedTraits = Set.of();
            return;
        }
        selectedTraits = Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public static boolean has(String traitPath) {
        return selectedTraits.contains("aftermath:" + traitPath);
    }

    private TraitClientState() {}
}
