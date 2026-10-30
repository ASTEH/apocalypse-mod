package dev.aftermath.state;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

public record PlayerStateDefinition(
        ResourceLocation id,
        String nameKey,
        String descriptionKey,
        ResourceLocation icon,
        double minValue,
        double maxValue,
        double initialValue,
        int updateIntervalTicks,
        List<Source> sources,
        List<Source> recovery,
        List<Interaction> interactions,
        List<Level> levels) {

    public PlayerStateDefinition {
        sources = List.copyOf(sources);
        recovery = List.copyOf(recovery);
        interactions = List.copyOf(interactions);
        levels = levels.stream().sorted((left, right) ->
                Integer.compare(right.priority(), left.priority())).toList();
    }

    public double clamp(double value) {
        return Math.max(minValue, Math.min(maxValue, value));
    }

    /** Highest-priority matching level wins. */
    public Level levelFor(double value) {
        for (Level level : levels) {
            if (level.matches(value)) return level;
        }
        return null;
    }

    public record Source(
            String type,
            double deltaPerSecond,
            double deltaPerEvent,
            double radius,
            int maxEntities,
            Map<String, Double> traitMultipliers) {
        public Source {
            traitMultipliers = Map.copyOf(traitMultipliers);
        }
    }

    public record Interaction(
            String sourceState,
            String operator,
            double threshold,
            double multiplier) {}

    public record Level(
            String id,
            int priority,
            String operator,
            double threshold,
            String nameKey,
            String descriptionKey,
            ResourceLocation icon,
            Map<String, Double> effects) {
        public Level {
            effects = Map.copyOf(effects);
        }

        public boolean matches(double value) {
            return switch (operator) {
                case "gte" -> value >= threshold;
                case "gt" -> value > threshold;
                case "lte" -> value <= threshold;
                case "lt" -> value < threshold;
                case "eq" -> Math.abs(value - threshold) < 0.0001D;
                default -> false;
            };
        }
    }
}
