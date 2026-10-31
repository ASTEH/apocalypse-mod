package dev.aftermath.skills;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.aftermath.character.AftermathAttachments;
import dev.aftermath.character.WeaponProgress;
import dev.aftermath.character.WeaponProgressionService;
import dev.aftermath.learning.LearningDefinitions;
import dev.aftermath.learning.LearningProgress;
import dev.aftermath.learning.LearningService;
import dev.aftermath.network.SkillsSnapshotPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Comparator;
import java.util.List;

/**
 * Builds the unified view from the existing learning and weapon progression stores.
 * This service never mutates player progression; the server remains its source of truth.
 */
public final class SkillsMenuService {
    private static final int MAX_SKILLS_IN_MENU = 64;

    public static void sendSnapshot(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new SkillsSnapshotPayload(buildSnapshotJson(player)));
    }

    public static String buildSnapshotJson(ServerPlayer player) {
        LearningProgress learning = player.getData(AftermathAttachments.LEARNING_PROGRESS);
        WeaponProgress weapons = player.getData(AftermathAttachments.WEAPON_PROGRESS);

        List<LearningDefinitions.SkillDefinition> definitions =
                LearningDefinitions.snapshot().skills().values().stream()
                        .sorted(Comparator.comparing(LearningDefinitions.SkillDefinition::category)
                                .thenComparing(skill -> skill.id().toString()))
                        .limit(MAX_SKILLS_IN_MENU)
                        .toList();

        JsonArray entries = new JsonArray();
        for (LearningDefinitions.SkillDefinition definition : definitions) {
            boolean weaponSkill = definition.progressionSource().equals("weapon");
            int experience = weaponSkill
                    ? weapons.experienceFor(definition.progressionKey())
                    : learning.experienceFor(definition.id().toString());

            int level = weaponSkill
                    ? WeaponProgressionService.levelFor(experience)
                    : LearningService.skillLevel(player, definition.id());
            int maxLevel = definition.maxLevel();
            int currentThreshold;
            int nextThreshold;
            if (weaponSkill) {
                currentThreshold = WeaponProgress.thresholdForLevel(level);
                nextThreshold = level >= maxLevel
                        ? currentThreshold : WeaponProgress.thresholdForLevel(level + 1);
            } else {
                List<Integer> thresholds = definition.xpThresholds();
                if (thresholds.isEmpty()) continue;
                int currentIndex = Math.max(0, Math.min(maxLevel - 1, level - 1));
                currentThreshold = thresholds.get(currentIndex);
                nextThreshold = level >= maxLevel
                        ? currentThreshold : thresholds.get(Math.min(maxLevel - 1, level));
            }

            boolean maxed = level >= maxLevel;
            int xpToNext = maxed ? 0 : Math.max(0, nextThreshold - experience);
            double progress = maxed ? 1.0D : progress(experience, currentThreshold, nextThreshold);

            JsonObject entry = new JsonObject();
            entry.addProperty("id", definition.id().toString());
            entry.addProperty("name", definition.nameKey());
            entry.addProperty("description", definition.descriptionKey());
            entry.addProperty("category", definition.category());
            entry.addProperty("level", level);
            entry.addProperty("max_level", maxLevel);
            entry.addProperty("experience", experience);
            entry.addProperty("xp_to_next", xpToNext);
            entry.addProperty("progress", progress);
            entries.add(entry);
        }

        JsonObject snapshot = new JsonObject();
        snapshot.addProperty("version", 1);
        snapshot.add("skills", entries);
        return snapshot.toString();
    }

    private static double progress(int experience, int currentThreshold, int nextThreshold) {
        int span = nextThreshold - currentThreshold;
        if (span <= 0) return 1.0D;
        return Math.max(0.0D, Math.min(1.0D,
                (experience - (double) currentThreshold) / span));
    }

    private SkillsMenuService() {}
}
