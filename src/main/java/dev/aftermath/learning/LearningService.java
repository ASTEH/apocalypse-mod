package dev.aftermath.learning;

import dev.aftermath.AftermathMod;
import dev.aftermath.character.AftermathAttachments;
import dev.aftermath.character.CharacterDefinitions;
import dev.aftermath.character.CharacterProfile;
import dev.aftermath.character.TraitRuntimeService;
import dev.aftermath.config.AftermathServerConfig;
import dev.aftermath.content.AftermathContent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-timed reading and TV sessions. Completion and XP are always authoritative on the server. */
public final class LearningService {
    private static final ResourceLocation FIELD_MEDICINE_PROGRAM =
            ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "field_medicine");
    private static final long DAY_TICKS = 24_000L;
    private static final Map<UUID, Session> ACTIVE_SESSIONS = new HashMap<>();

    public static void startReading(ServerPlayer player, ResourceLocation lessonId, InteractionHand hand) {
        if (!AftermathServerConfig.ENABLE_LEARNING.get()) {
            message(player, "message.aftermath.learning.disabled");
            return;
        }
        if (ACTIVE_SESSIONS.containsKey(player.getUUID())) {
            message(player, "message.aftermath.learning.already_active");
            return;
        }
        if (!TraitRuntimeService.canRead(player)) {
            message(player, "message.aftermath.learning.illiterate");
            return;
        }

        LearningDefinitions.LessonDefinition lesson = LearningDefinitions.snapshot().lessons().get(lessonId);
        if (lesson == null) {
            AftermathMod.LOGGER.error("No loaded Aftermath lesson definition for {}.", lessonId);
            message(player, "message.aftermath.learning.unavailable");
            return;
        }

        if (!BuiltInRegistries.ITEM.getKey(player.getItemInHand(hand).getItem()).equals(lesson.itemId())) {
            message(player, "message.aftermath.learning.wrong_manual");
            return;
        }

        LearningProgress progress = player.getData(AftermathAttachments.LEARNING_PROGRESS);
        if (progress.lessonCompletionsFor(lesson.id().toString()) >= lesson.maxCompletions()) {
            message(player, "message.aftermath.learning.lesson_completed");
            return;
        }

        int readingDuration = TraitRuntimeService.adjustedReadingDuration(player, lesson.durationTicks());
        ACTIVE_SESSIONS.put(player.getUUID(), new Session(
                SessionType.READING,
                lesson.id(),
                lesson.titleKey(),
                lesson.skillId(),
                lesson.itemId(),
                hand,
                null,
                player.level().dimension().location(),
                player.position(),
                player.level().getGameTime(),
                readingDuration,
                lesson.xpReward(),
                lesson.maxCompletions(),
                lesson.cancelOnMovement(),
                lesson.cancelOnDamage()));
        player.displayClientMessage(Component.translatable(
                "message.aftermath.learning.started", Component.translatable(lesson.titleKey()),
                seconds(readingDuration)), true);
    }

    public static void startTelevision(Player player, net.minecraft.core.BlockPos televisionPos) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (!AftermathServerConfig.ENABLE_LEARNING.get()) {
            message(serverPlayer, "message.aftermath.learning.disabled");
            return;
        }
        if (ACTIVE_SESSIONS.containsKey(serverPlayer.getUUID())) {
            message(serverPlayer, "message.aftermath.learning.already_active");
            return;
        }

        LearningDefinitions.TvProgramDefinition program =
                LearningDefinitions.snapshot().programs().get(FIELD_MEDICINE_PROGRAM);
        if (program == null) {
            AftermathMod.LOGGER.error("No loaded Aftermath television program definition for {}.", FIELD_MEDICINE_PROGRAM);
            message(serverPlayer, "message.aftermath.learning.unavailable");
            return;
        }

        LearningProgress progress = serverPlayer.getData(AftermathAttachments.LEARNING_PROGRESS);
        if (progress.programCompletionsFor(program.id().toString()) >= program.maxCompletions()) {
            message(serverPlayer, "message.aftermath.learning.program_completed");
            return;
        }

        long dayTime = Math.floorMod(serverPlayer.level().getDayTime(), DAY_TICKS);
        long broadcastEnd = (long) program.startTime() + program.durationTicks();
        if (dayTime < program.startTime() || dayTime >= broadcastEnd) {
            message(serverPlayer, "message.aftermath.learning.tv_off_air");
            return;
        }

        ACTIVE_SESSIONS.put(serverPlayer.getUUID(), new Session(
                SessionType.TELEVISION,
                program.id(),
                program.titleKey(),
                program.skillId(),
                null,
                null,
                televisionPos.immutable(),
                serverPlayer.level().dimension().location(),
                serverPlayer.position(),
                serverPlayer.level().getGameTime(),
                program.durationTicks(),
                program.xpReward(),
                program.maxCompletions(),
                true,
                true));
        serverPlayer.displayClientMessage(Component.translatable(
                "message.aftermath.learning.watching", Component.translatable(program.titleKey()),
                seconds(program.durationTicks())), true);
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Session session = ACTIVE_SESSIONS.get(player.getUUID());
        if (session == null) return;

        if (!AftermathServerConfig.ENABLE_LEARNING.get()) {
            cancel(player, "message.aftermath.learning.cancelled");
            return;
        }
        if (!player.level().dimension().location().equals(session.dimension())) {
            cancel(player, "message.aftermath.learning.cancelled");
            return;
        }
        if (session.cancelOnMovement() && player.position().distanceToSqr(session.startPosition()) > 0.01D) {
            cancel(player, "message.aftermath.learning.cancelled_movement");
            return;
        }

        if (session.type() == SessionType.READING) {
            if (session.hand() == null || !BuiltInRegistries.ITEM.getKey(
                    player.getItemInHand(session.hand()).getItem()).equals(session.sourceItemId())) {
                cancel(player, "message.aftermath.learning.cancelled_manual");
                return;
            }
        } else {
            if (session.televisionPos() == null
                    || !player.level().getBlockState(session.televisionPos())
                    .is(AftermathContent.TRAINING_TELEVISION.get())) {
                cancel(player, "message.aftermath.learning.cancelled");
                return;
            }
        }

        long elapsed = player.level().getGameTime() - session.startedAt();
        if (elapsed < 0) {
            cancel(player, "message.aftermath.learning.cancelled");
            return;
        }
        if (elapsed >= session.durationTicks()) {
            complete(player, session);
            return;
        }
        if (elapsed > 0 && elapsed % 100L == 0L) {
            player.displayClientMessage(Component.translatable(
                    "message.aftermath.learning.progress",
                    Component.translatable(session.titleKey()),
                    seconds((int) elapsed),
                    seconds(session.durationTicks())), true);
        }
    }

    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Session session = ACTIVE_SESSIONS.get(player.getUUID());
        if (session == null || !session.cancelOnDamage()) return;
        cancel(player, "message.aftermath.learning.cancelled_damage");
    }

    public static void onPlayerLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE_SESSIONS.remove(event.getEntity().getUUID());
    }

    public static Component buildStatusMessage(ServerPlayer player) {
        LearningProgress progress = player.getData(AftermathAttachments.LEARNING_PROGRESS);
        return Component.translatable("message.aftermath.learning.status",
                progress.experienceFor("aftermath:first_aid"),
                progress.experienceFor("aftermath:firearms"),
                progress.experienceFor("aftermath:mechanics"));
    }

    private static void complete(ServerPlayer player, Session session) {
        ACTIVE_SESSIONS.remove(player.getUUID());
        LearningProgress progress = player.getData(AftermathAttachments.LEARNING_PROGRESS);
        int currentCompletions = session.type() == SessionType.READING
                ? progress.lessonCompletionsFor(session.definitionId().toString())
                : progress.programCompletionsFor(session.definitionId().toString());
        if (currentCompletions >= session.maxCompletions()) {
            message(player, "message.aftermath.learning.already_claimed");
            return;
        }

        int reward = Math.max(0, (int) Math.round(session.baseXpReward() * learningMultiplier(player, session.skillId())));
        LearningProgress updated = session.type() == SessionType.READING
                ? progress.completeLesson(session.definitionId().toString(), session.skillId().toString(), reward)
                : progress.completeProgram(session.definitionId().toString(), session.skillId().toString(), reward);
        player.setData(AftermathAttachments.LEARNING_PROGRESS, updated);
        player.displayClientMessage(Component.translatable(
                "message.aftermath.learning.completed",
                Component.translatable(session.titleKey()),
                reward,
                updated.experienceFor(session.skillId().toString())), false);
        AftermathMod.LOGGER.info("Player {} completed Aftermath {} {} and received {} XP.",
                player.getGameProfile().getName(), session.type().name().toLowerCase(),
                session.definitionId(), reward);
    }

    private static double learningMultiplier(ServerPlayer player, ResourceLocation skillId) {
        CharacterProfile profile = player.getData(AftermathAttachments.CHARACTER_PROFILE);
        double multiplier = 1.0D;
        for (String rawId : profile.traitIds()) {
            ResourceLocation traitId = ResourceLocation.tryParse(rawId);
            if (traitId == null) continue;
            CharacterDefinitions.TraitDefinition trait = CharacterDefinitions.snapshot().traits().get(traitId);
            if (trait == null) continue;
            for (CharacterDefinitions.TraitEffect effect : trait.effects()) {
                if (effect.type().equals("learning_xp_multiplier")) multiplier *= effect.value();
                if (effect.type().equals("skill_xp_multiplier") && effect.target().equals(skillId.toString())) {
                    multiplier *= effect.value();
                }
            }
        }
        return Math.max(0.25D, Math.min(3.0D, multiplier));
    }

    private static void cancel(ServerPlayer player, String key) {
        if (ACTIVE_SESSIONS.remove(player.getUUID()) != null) message(player, key);
    }

    private static void message(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key), true);
    }

    private static int seconds(int ticks) {
        return (int) Math.ceil(Math.max(0, ticks) / 20.0D);
    }

    private enum SessionType { READING, TELEVISION }

    private record Session(
            SessionType type,
            ResourceLocation definitionId,
            String titleKey,
            ResourceLocation skillId,
            ResourceLocation sourceItemId,
            InteractionHand hand,
            net.minecraft.core.BlockPos televisionPos,
            ResourceLocation dimension,
            Vec3 startPosition,
            long startedAt,
            int durationTicks,
            int baseXpReward,
            int maxCompletions,
            boolean cancelOnMovement,
            boolean cancelOnDamage) {}

    private LearningService() {}
}
