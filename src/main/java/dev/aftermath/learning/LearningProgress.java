package dev.aftermath.learning;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

/** Persistent skill XP, cooldowns and timed learning bonuses. All mutations are server-side. */
public record LearningProgress(
        Map<String, Integer> skillExperience,
        Map<String, Integer> lessonCompletions,
        Map<String, Integer> programCompletions,
        Map<String, Long> programLastCompletedAt,
        Map<String, Double> skillBookMultipliers,
        Map<String, Long> skillBookExpiresAt,
        Map<String, Long> actionCooldowns,
        Map<String, Integer> actionDailyXp) {

    public static final Codec<LearningProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT)
                    .optionalFieldOf("skill_experience", Map.of())
                    .forGetter(LearningProgress::skillExperience),
            Codec.unboundedMap(Codec.STRING, Codec.INT)
                    .optionalFieldOf("lesson_completions", Map.of())
                    .forGetter(LearningProgress::lessonCompletions),
            Codec.unboundedMap(Codec.STRING, Codec.INT)
                    .optionalFieldOf("program_completions", Map.of())
                    .forGetter(LearningProgress::programCompletions),
            Codec.unboundedMap(Codec.STRING, Codec.LONG)
                    .optionalFieldOf("program_last_completed_at", Map.of())
                    .forGetter(LearningProgress::programLastCompletedAt),
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE)
                    .optionalFieldOf("skill_book_multipliers", Map.of())
                    .forGetter(LearningProgress::skillBookMultipliers),
            Codec.unboundedMap(Codec.STRING, Codec.LONG)
                    .optionalFieldOf("skill_book_expires_at", Map.of())
                    .forGetter(LearningProgress::skillBookExpiresAt),
            Codec.unboundedMap(Codec.STRING, Codec.LONG)
                    .optionalFieldOf("action_cooldowns", Map.of())
                    .forGetter(LearningProgress::actionCooldowns),
            Codec.unboundedMap(Codec.STRING, Codec.INT)
                    .optionalFieldOf("action_daily_xp", Map.of())
                    .forGetter(LearningProgress::actionDailyXp)
    ).apply(instance, LearningProgress::new));

    /** Compatibility with older code and old saves that only contain the original three maps. */
    public LearningProgress(Map<String, Integer> skillExperience,
                            Map<String, Integer> lessonCompletions,
                            Map<String, Integer> programCompletions) {
        this(skillExperience, lessonCompletions, programCompletions,
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }

    public LearningProgress {
        skillExperience = sanitizeInts(skillExperience);
        lessonCompletions = sanitizeInts(lessonCompletions);
        programCompletions = sanitizeInts(programCompletions);
        programLastCompletedAt = sanitizeLongs(programLastCompletedAt);
        skillBookMultipliers = sanitizeMultipliers(skillBookMultipliers);
        skillBookExpiresAt = sanitizeLongs(skillBookExpiresAt);
        actionCooldowns = sanitizeLongs(actionCooldowns);
        actionDailyXp = sanitizeInts(actionDailyXp);
    }

    private static Map<String, Integer> sanitizeInts(Map<String, Integer> source) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (source != null) source.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null && value >= 0) result.put(key, value);
        });
        return Map.copyOf(result);
    }

    private static Map<String, Long> sanitizeLongs(Map<String, Long> source) {
        Map<String, Long> result = new LinkedHashMap<>();
        if (source != null) source.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null && value >= 0L) result.put(key, value);
        });
        return Map.copyOf(result);
    }

    private static Map<String, Double> sanitizeMultipliers(Map<String, Double> source) {
        Map<String, Double> result = new LinkedHashMap<>();
        if (source != null) source.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null
                    && Double.isFinite(value) && value >= 1.0D && value <= 5.0D) {
                result.put(key, value);
            }
        });
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

    public long programLastCompletedAt(String programId) {
        return programLastCompletedAt.getOrDefault(programId, 0L);
    }

    public boolean canClaimProgram(String programId, int maxCompletions, long now, int repeatAfterDays) {
        if (programCompletionsFor(programId) >= maxCompletions) return false;
        long last = programLastCompletedAt(programId);
        return last == 0L || now < last || now - last >= repeatAfterDays * 24_000L;
    }

    public double bookMultiplierFor(String skillId, long now) {
        long expiresAt = skillBookExpiresAt.getOrDefault(skillId, 0L);
        if (expiresAt <= now) return 1.0D;
        return skillBookMultipliers.getOrDefault(skillId, 1.0D);
    }

    public LearningProgress activateBook(String skillId, double multiplier, long expiresAt) {
        Map<String, Double> updatedMultipliers = new LinkedHashMap<>(skillBookMultipliers);
        Map<String, Long> updatedExpiries = new LinkedHashMap<>(skillBookExpiresAt);
        updatedMultipliers.put(skillId, Math.max(1.0D, Math.min(5.0D, multiplier)));
        updatedExpiries.put(skillId, Math.max(0L, expiresAt));
        return copy(skillExperience, lessonCompletions, programCompletions, programLastCompletedAt,
                updatedMultipliers, updatedExpiries, actionCooldowns, actionDailyXp);
    }

    public LearningProgress expireBookBuffs(long now) {
        if (skillBookExpiresAt.isEmpty()) return this;
        Map<String, Double> multipliers = new LinkedHashMap<>(skillBookMultipliers);
        Map<String, Long> expiries = new LinkedHashMap<>(skillBookExpiresAt);
        boolean changed = false;
        for (Map.Entry<String, Long> entry : skillBookExpiresAt.entrySet()) {
            if (entry.getValue() <= now) {
                expiries.remove(entry.getKey());
                multipliers.remove(entry.getKey());
                changed = true;
            }
        }
        return changed ? copy(skillExperience, lessonCompletions, programCompletions, programLastCompletedAt,
                multipliers, expiries, actionCooldowns, actionDailyXp) : this;
    }

    public LearningProgress completeLesson(String lessonId, String skillId, int xp) {
        Map<String, Integer> updatedXp = new LinkedHashMap<>(skillExperience);
        updatedXp.put(skillId, safeAdd(experienceFor(skillId), xp));
        Map<String, Integer> updatedLessons = new LinkedHashMap<>(lessonCompletions);
        updatedLessons.put(lessonId, lessonCompletionsFor(lessonId) + 1);
        return copy(updatedXp, updatedLessons, programCompletions, programLastCompletedAt,
                skillBookMultipliers, skillBookExpiresAt, actionCooldowns, actionDailyXp);
    }

    public LearningProgress completeProgram(String programId, String skillId, int xp) {
        return completeProgram(programId, skillId, xp, 0L);
    }

    public LearningProgress completeProgram(String programId, String skillId, int xp, long completedAt) {
        Map<String, Integer> updatedXp = new LinkedHashMap<>(skillExperience);
        updatedXp.put(skillId, safeAdd(experienceFor(skillId), xp));
        Map<String, Integer> updatedPrograms = new LinkedHashMap<>(programCompletions);
        updatedPrograms.put(programId, programCompletionsFor(programId) + 1);
        Map<String, Long> updatedCompletedAt = new LinkedHashMap<>(programLastCompletedAt);
        updatedCompletedAt.put(programId, Math.max(0L, completedAt));
        return copy(updatedXp, lessonCompletions, updatedPrograms, updatedCompletedAt,
                skillBookMultipliers, skillBookExpiresAt, actionCooldowns, actionDailyXp);
    }

    public LearningProgress addActionExperience(String skillId, int xp, String actionId,
                                                long now, String dailyKey) {
        Map<String, Integer> updatedXp = new LinkedHashMap<>(skillExperience);
        updatedXp.put(skillId, safeAdd(experienceFor(skillId), xp));
        Map<String, Long> updatedCooldowns = new LinkedHashMap<>(actionCooldowns);
        updatedCooldowns.put(actionId, Math.max(0L, now));
        Map<String, Integer> updatedDaily = new LinkedHashMap<>(actionDailyXp);
        updatedDaily.put(dailyKey, safeAdd(updatedDaily.getOrDefault(dailyKey, 0), xp));
        if (updatedDaily.size() > 128) {
            updatedDaily.keySet().removeIf(key -> !key.equals(dailyKey) && !key.equals(actionId));
            while (updatedDaily.size() > 64) {
                updatedDaily.remove(updatedDaily.keySet().iterator().next());
            }
        }
        return copy(updatedXp, lessonCompletions, programCompletions, programLastCompletedAt,
                skillBookMultipliers, skillBookExpiresAt, updatedCooldowns, updatedDaily);
    }

    public long actionLastUsedAt(String actionId) {
        return actionCooldowns.getOrDefault(actionId, 0L);
    }

    public int actionDailyXp(String dailyKey) {
        return actionDailyXp.getOrDefault(dailyKey, 0);
    }

    private LearningProgress copy(Map<String, Integer> xp,
                                  Map<String, Integer> lessons,
                                  Map<String, Integer> programs,
                                  Map<String, Long> programTimes,
                                  Map<String, Double> multipliers,
                                  Map<String, Long> expiries,
                                  Map<String, Long> cooldowns,
                                  Map<String, Integer> dailyXp) {
        return new LearningProgress(xp, lessons, programs, programTimes,
                multipliers, expiries, cooldowns, dailyXp);
    }

    private static int safeAdd(int left, int right) {
        return (int) Math.min(2_000_000_000L, (long) Math.max(0, left) + Math.max(0, right));
    }
}