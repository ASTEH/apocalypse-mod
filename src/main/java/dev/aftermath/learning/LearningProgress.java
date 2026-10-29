package dev.aftermath.learning;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

/** Persistent learned skill XP and completion counts. All mutations happen on the server. */
public record LearningProgress(
        Map<String, Integer> skillExperience,
        Map<String, Integer> lessonCompletions,
        Map<String, Integer> programCompletions) {

    public static final Codec<LearningProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT)
                    .optionalFieldOf("skill_experience", Map.of())
                    .forGetter(LearningProgress::skillExperience),
            Codec.unboundedMap(Codec.STRING, Codec.INT)
                    .optionalFieldOf("lesson_completions", Map.of())
                    .forGetter(LearningProgress::lessonCompletions),
            Codec.unboundedMap(Codec.STRING, Codec.INT)
                    .optionalFieldOf("program_completions", Map.of())
                    .forGetter(LearningProgress::programCompletions)
    ).apply(instance, LearningProgress::new));

    public LearningProgress {
        skillExperience = sanitize(skillExperience);
        lessonCompletions = sanitize(lessonCompletions);
        programCompletions = sanitize(programCompletions);
    }

    private static Map<String, Integer> sanitize(Map<String, Integer> source) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (source != null) {
            source.forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null && value >= 0) {
                    result.put(key, value);
                }
            });
        }
        return Map.copyOf(result);
    }

    public static LearningProgress empty() {
        return new LearningProgress(Map.of(), Map.of(), Map.of());
    }

    public int experienceFor(String skillId) {
        return skillExperience.getOrDefault(skillId, 0);
    }

    public int lessonCompletionsFor(String lessonId) {
        return lessonCompletions.getOrDefault(lessonId, 0);
    }

    public int programCompletionsFor(String programId) {
        return programCompletions.getOrDefault(programId, 0);
    }

    public LearningProgress completeLesson(String lessonId, String skillId, int xp) {
        Map<String, Integer> updatedXp = new LinkedHashMap<>(skillExperience);
        updatedXp.put(skillId, safeAdd(experienceFor(skillId), xp));
        Map<String, Integer> updatedLessons = new LinkedHashMap<>(lessonCompletions);
        updatedLessons.put(lessonId, lessonCompletionsFor(lessonId) + 1);
        return new LearningProgress(updatedXp, updatedLessons, programCompletions);
    }

    public LearningProgress completeProgram(String programId, String skillId, int xp) {
        Map<String, Integer> updatedXp = new LinkedHashMap<>(skillExperience);
        updatedXp.put(skillId, safeAdd(experienceFor(skillId), xp));
        Map<String, Integer> updatedPrograms = new LinkedHashMap<>(programCompletions);
        updatedPrograms.put(programId, programCompletionsFor(programId) + 1);
        return new LearningProgress(updatedXp, lessonCompletions, updatedPrograms);
    }

    private static int safeAdd(int left, int right) {
        return (int) Math.min(2_000_000_000L, (long) Math.max(0, left) + Math.max(0, right));
    }
}
