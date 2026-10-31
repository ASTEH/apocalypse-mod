package dev.aftermath.learning;

import dev.aftermath.AftermathMod;
import dev.aftermath.character.AftermathAttachments;
import dev.aftermath.character.WeaponProgressionService;
import dev.aftermath.character.CharacterDefinitions;
import dev.aftermath.character.CharacterProfile;
import dev.aftermath.character.TraitRuntimeService;
import dev.aftermath.config.AftermathServerConfig;
import dev.aftermath.content.AftermathContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AnvilRepairEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;

/** Server-timed learning sessions and action-based skill progression. */
public final class LearningService {
    private static final long DAY_TICKS = 24_000L;
    private static final int SKILL_DAILY_XP_CAP = 120;
    private static final String LOOT_CONTAINER_MARKER = "AftermathLootContainer";
    private static final long DRIVING_SAMPLE_TICKS = 200L;
    private static final double MIN_DRIVING_DISTANCE_SQR = 256.0D;
    private static final List<String> SKILL_STATUS_ORDER = List.of(
            "cooking", "farming", "mechanics", "first_aid",
            "driving", "firearms", "melee", "scavenging");
    private static final TagKey<Item> COOKING_ITEMS = TagKey.create(
            Registries.ITEM, ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "learning/cooking"));
    private static final TagKey<Item> MECHANICS_ITEMS = TagKey.create(
            Registries.ITEM, ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "learning/mechanics"));
    private static final Map<UUID, Session> ACTIVE_SESSIONS = new HashMap<>();
    private static final Map<UUID, DrivingSample> DRIVING_SAMPLES = new HashMap<>();
    private static final Map<UUID, LootingSession> LOOTING_SESSIONS = new HashMap<>();
    private static final Map<UUID, PendingLoot> PENDING_LOOT_CONTAINERS = new HashMap<>();

    public static void startReading(ServerPlayer player, ResourceLocation lessonId, InteractionHand hand) {
        if (!canStartSession(player)) return;
        LearningDefinitions.LessonDefinition lesson = LearningDefinitions.snapshot().lessons().get(lessonId);
        if (lesson == null) {
            message(player, "message.aftermath.learning.unavailable");
            return;
        }
        if (!isHeldItem(player, hand, lesson.itemId())) {
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
                SessionType.READING, lesson.id(), lesson.titleKey(), lesson.skillId(), lesson.itemId(),
                hand, null, player.level().dimension().location(), player.position(),
                player.level().getGameTime(), readingDuration, lesson.xpReward(), lesson.maxCompletions(),
                lesson.cancelOnMovement(), lesson.cancelOnDamage(), 1.0D, 0, false, null, null));
        message(player, "message.aftermath.learning.started",
                Component.translatable(lesson.titleKey()), seconds(readingDuration));
    }

    /** Reading a skill book activates a timed multiplier; it never grants XP immediately. */
    public static void startBookReading(ServerPlayer player, ResourceLocation bookId, InteractionHand hand) {
        if (!canStartSession(player)) return;
        if (!TraitRuntimeService.canRead(player)) {
            message(player, "message.aftermath.learning.illiterate");
            return;
        }

        LearningDefinitions.BookDefinition book = LearningDefinitions.snapshot().books().get(bookId);
        if (book == null) {
            AftermathMod.LOGGER.error("No loaded learning book definition for {}.", bookId);
            message(player, "message.aftermath.learning.unavailable");
            return;
        }
        if (!isHeldItem(player, hand, book.itemId())) {
            message(player, "message.aftermath.learning.wrong_manual");
            return;
        }

        int level = skillLevel(player, book.skillId());
        if (level < book.requiredSkillLevel()) {
            message(player, "message.aftermath.learning.book_level_required",
                    Component.translatable(book.titleKey()), book.requiredSkillLevel(), level);
            return;
        }

        int readingDuration = TraitRuntimeService.adjustedReadingDuration(player, book.readingTicks());
        ACTIVE_SESSIONS.put(player.getUUID(), new Session(
                SessionType.BOOK, book.id(), book.titleKey(), book.skillId(), book.itemId(),
                hand, null, player.level().dimension().location(), player.position(),
                player.level().getGameTime(), readingDuration, 0, 1,
                true, true, book.multiplier(), book.buffDurationTicks(), false, null, null));
        message(player, "message.aftermath.learning.started",
                Component.translatable(book.titleKey()), seconds(readingDuration));
    }

    public static void startTelevision(Player player, net.minecraft.core.BlockPos televisionPos) {
        if (!(player instanceof ServerPlayer serverPlayer) || !canStartSession(serverPlayer)) return;
        long dayTime = Math.floorMod(serverPlayer.level().getDayTime(), DAY_TICKS);
        long now = serverPlayer.getServer().overworld().getGameTime();
        LearningProgress progress = serverPlayer.getData(AftermathAttachments.LEARNING_PROGRESS);

        LearningDefinitions.TvProgramDefinition onAir = LearningDefinitions.snapshot().programs().values().stream()
                .filter(program -> isBroadcasting(program, dayTime))
                .filter(program -> progress.canClaimProgram(program.id().toString(), program.maxCompletions(),
                        now, program.repeatAfterDays()))
                .sorted(java.util.Comparator.comparing(program -> program.id().toString()))
                .findFirst().orElse(null);

        if (onAir == null) {
            boolean scheduleExists = LearningDefinitions.snapshot().programs().values().stream()
                    .anyMatch(program -> isBroadcasting(program, dayTime));
            message(serverPlayer, scheduleExists
                    ? "message.aftermath.learning.program_completed"
                    : "message.aftermath.learning.tv_off_air");
            return;
        }

        ACTIVE_SESSIONS.put(serverPlayer.getUUID(), new Session(
                SessionType.TELEVISION, onAir.id(), onAir.titleKey(), onAir.skillId(), null,
                null, televisionPos.immutable(), serverPlayer.level().dimension().location(),
                serverPlayer.position(), serverPlayer.level().getGameTime(), onAir.durationTicks(),
                onAir.xpReward(), onAir.maxCompletions(), true, true,
                1.0D, 0, false, null, null));
        message(serverPlayer, "message.aftermath.learning.watching",
                Component.translatable(onAir.titleKey()), seconds(onAir.durationTicks()));
    }

    /** Inserts a valid data-driven VHS cassette into the television's persistent slot. */
    public static void insertVhsTape(ServerPlayer player, net.minecraft.core.BlockPos televisionPos,
                                     InteractionHand hand) {
        if (!AftermathServerConfig.ENABLE_LEARNING.get()) {
            message(player, "message.aftermath.learning.disabled");
            return;
        }
        if (ACTIVE_SESSIONS.containsKey(player.getUUID())) {
            message(player, "message.aftermath.learning.already_active");
            return;
        }
        if (!(player.level().getBlockEntity(televisionPos) instanceof TrainingTelevisionBlockEntity television)) {
            message(player, "message.aftermath.learning.unavailable");
            return;
        }
        if (television.hasCassette()) {
            message(player, "message.aftermath.learning.vhs_slot_full");
            return;
        }

        ItemStack held = player.getItemInHand(hand);
        if (!held.is(AftermathContent.VHS_TAPE.get())) return;
        ResourceLocation tapeId = TrainingVhsTapeItem.readTapeId(held);
        LearningDefinitions.VhsTapeDefinition tape = tapeId == null
                ? null : LearningDefinitions.snapshot().vhsTapes().get(tapeId);
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(held.getItem());
        if (tape == null || !tape.itemId().equals(itemId)) {
            message(player, "message.aftermath.learning.vhs_invalid");
            return;
        }

        ItemStack cassette = held.copy();
        cassette.setCount(1);
        if (!television.insertCassette(cassette)) {
            message(player, "message.aftermath.learning.vhs_slot_full");
            return;
        }
        if (!player.getAbilities().instabuild) held.shrink(1);
        message(player, "message.aftermath.learning.vhs_inserted");
    }

    /** Starts playback from the cassette currently inserted in the television. */
    public static void startVhsTape(ServerPlayer player, net.minecraft.core.BlockPos televisionPos) {
        if (!canStartSession(player)) return;
        if (!(player.level().getBlockEntity(televisionPos) instanceof TrainingTelevisionBlockEntity television)
                || !television.hasCassette()) {
            message(player, "message.aftermath.learning.vhs_empty");
            return;
        }

        ItemStack cassette = television.getCassette();
        ResourceLocation tapeId = TrainingVhsTapeItem.readTapeId(cassette);
        LearningDefinitions.VhsTapeDefinition tape = tapeId == null
                ? null : LearningDefinitions.snapshot().vhsTapes().get(tapeId);
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(cassette.getItem());
        if (tape == null || !tape.itemId().equals(itemId)
                || !television.matchesCassette(tape.itemId(), tape.id())) {
            message(player, "message.aftermath.learning.vhs_invalid");
            return;
        }

        LearningDefinitions.TvProgramDefinition program =
                LearningDefinitions.snapshot().programs().get(tape.programId());
        if (program == null) {
            message(player, "message.aftermath.learning.unavailable");
            return;
        }

        long now = player.getServer().overworld().getGameTime();
        LearningProgress progress = player.getData(AftermathAttachments.LEARNING_PROGRESS);
        if (!progress.canClaimProgram(program.id().toString(), program.maxCompletions(),
                now, program.repeatAfterDays())) {
            message(player, "message.aftermath.learning.program_completed");
            return;
        }

        ACTIVE_SESSIONS.put(player.getUUID(), new Session(
                SessionType.VHS, program.id(), tape.titleKey(), program.skillId(), tape.itemId(),
                null, televisionPos.immutable(), player.level().dimension().location(),
                player.position(), player.level().getGameTime(), program.durationTicks(),
                program.xpReward(), program.maxCompletions(), true, true,
                1.0D, 0, tape.consumeOnUse(), TrainingVhsTapeItem.DATA_KEY, tape.id().toString()));
        message(player, "message.aftermath.learning.watching",
                Component.translatable(tape.titleKey()), seconds(program.durationTicks()));
    }

    /** Sneak-right-clicking the television returns the cassette to the player. */
    public static void ejectVhsTape(ServerPlayer player, net.minecraft.core.BlockPos televisionPos) {
        if (!(player.level().getBlockEntity(televisionPos) instanceof TrainingTelevisionBlockEntity television)
                || !television.hasCassette()) {
            message(player, "message.aftermath.learning.vhs_empty");
            return;
        }

        Session session = ACTIVE_SESSIONS.get(player.getUUID());
        if (session != null && session.type() == SessionType.VHS
                && televisionPos.equals(session.televisionPos())) {
            ACTIVE_SESSIONS.remove(player.getUUID());
        }

        ItemStack cassette = television.removeCassette();
        if (!player.getInventory().add(cassette) && !cassette.isEmpty()) {
            player.drop(cassette, false);
        }
        message(player, "message.aftermath.learning.vhs_ejected");
    }

    private static boolean canStartSession(ServerPlayer player) {
        if (!AftermathServerConfig.ENABLE_LEARNING.get()) {
            message(player, "message.aftermath.learning.disabled");
            return false;
        }
        if (ACTIVE_SESSIONS.containsKey(player.getUUID())) {
            message(player, "message.aftermath.learning.already_active");
            return false;
        }
        return true;
    }

    private static boolean isBroadcasting(LearningDefinitions.TvProgramDefinition program, long dayTime) {
        long elapsed = Math.floorMod(dayTime - program.startTime(), DAY_TICKS);
        return elapsed < program.durationTicks();
    }

    private static boolean isHeldItem(ServerPlayer player, InteractionHand hand, ResourceLocation expected) {
        return hand != null && BuiltInRegistries.ITEM.getKey(player.getItemInHand(hand).getItem()).equals(expected);
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        long now = player.getServer().overworld().getGameTime();
        LearningProgress progress = player.getData(AftermathAttachments.LEARNING_PROGRESS);
        LearningProgress expired = progress.expireBookBuffs(now);
        if (expired != progress) player.setData(AftermathAttachments.LEARNING_PROGRESS, expired);

        tickDriving(player, now);
        tickLooting(player);
        Session session = ACTIVE_SESSIONS.get(player.getUUID());
        if (session == null) return;

        if (!AftermathServerConfig.ENABLE_LEARNING.get()
                || !player.level().dimension().location().equals(session.dimension())) {
            cancel(player, "message.aftermath.learning.cancelled");
            return;
        }
        if (session.cancelOnMovement() && player.position().distanceToSqr(session.startPosition()) > 0.01D) {
            cancel(player, "message.aftermath.learning.cancelled_movement");
            return;
        }

        if (session.type() == SessionType.TELEVISION || session.type() == SessionType.VHS) {
            if (session.televisionPos() == null
                    || !player.level().getBlockState(session.televisionPos()).is(AftermathContent.TRAINING_TELEVISION.get())) {
                cancel(player, "message.aftermath.learning.cancelled");
                return;
            }
            if (session.type() == SessionType.VHS) {
                ResourceLocation tapeId = ResourceLocation.tryParse(session.sourceDataValue());
                if (!(player.level().getBlockEntity(session.televisionPos())
                        instanceof TrainingTelevisionBlockEntity television)
                        || !television.matchesCassette(session.sourceItemId(), tapeId)) {
                    cancel(player, "message.aftermath.learning.vhs_invalid");
                    return;
                }
            }
        } else if (!isHeldItem(player, session.hand(), session.sourceItemId())) {
            cancel(player, "message.aftermath.learning.cancelled_manual");
            return;
        }

        long elapsed = player.level().getGameTime() - session.startedAt();
        if (elapsed < 0L) {
            cancel(player, "message.aftermath.learning.cancelled");
            return;
        }
        if (elapsed >= session.durationTicks()) {
            complete(player, session);
            return;
        }
        if (elapsed > 0L && elapsed % 100L == 0L) {
            message(player, "message.aftermath.learning.progress",
                    Component.translatable(session.titleKey()), seconds((int) elapsed),
                    seconds(session.durationTicks()));
        }
    }

    private static void tickDriving(ServerPlayer player, long now) {
        if (player.tickCount % 20 != 0) return;
        Entity vehicle = player.getVehicle();
        if (vehicle == null) {
            DRIVING_SAMPLES.remove(player.getUUID());
            return;
        }

        ResourceLocation vehicleId = BuiltInRegistries.ENTITY_TYPE.getKey(vehicle.getType());
        String namespace = vehicleId.getNamespace();
        boolean supportedVehicle = (namespace.equals("mts") && ModList.get().isLoaded("mts"))
                || (namespace.equals("immersivevehicles") && ModList.get().isLoaded("immersivevehicles"));
        if (!supportedVehicle) {
            DRIVING_SAMPLES.remove(player.getUUID());
            return;
        }

        DrivingSample previous = DRIVING_SAMPLES.get(player.getUUID());
        if (previous == null || now < previous.sampledAt()) {
            DRIVING_SAMPLES.put(player.getUUID(), new DrivingSample(vehicle.position(), now));
            return;
        }
        if (now - previous.sampledAt() < DRIVING_SAMPLE_TICKS) return;

        double distanceSqr = vehicle.position().distanceToSqr(previous.position());
        DRIVING_SAMPLES.put(player.getUUID(), new DrivingSample(vehicle.position(), now));
        if (distanceSqr >= MIN_DRIVING_DISTANCE_SQR) {
            awardSkillXp(player, id("driving"), 5, "driving_distance", 200L, 40);
        }
    }

    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack crafted = event.getCrafting();
        if (crafted.is(COOKING_ITEMS)) {
            awardSkillXp(player, id("cooking"), Math.min(6, Math.max(1, crafted.getCount())),
                    "cooking_craft", 20L, 60);
        }
        if (crafted.is(MECHANICS_ITEMS)) {
            awardSkillXp(player, id("mechanics"), Math.min(5, Math.max(1, crafted.getCount())),
                    "mechanics_craft", 40L, 50);
        }
    }

    public static void onItemSmelted(PlayerEvent.ItemSmeltedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack smelted = event.getSmelting();
        if (smelted.is(COOKING_ITEMS)) {
            awardSkillXp(player, id("cooking"), Math.min(6, Math.max(1, smelted.getCount())),
                    "cooking_smelt", 20L, 80);
        }
    }

    public static void onCropBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (event.isCanceled()) return;
        if (event.getState().getBlock() instanceof CropBlock crop && crop.isMaxAge(event.getState())) {
            awardSkillXp(player, id("farming"), 4, "mature_crop_harvest", 20L, 60);
        }
    }

    public static void onAnvilRepair(AnvilRepairEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getOutput().isEmpty()) return;
        if (event.getOutput().isDamageableItem() || event.getOutput().isEnchanted()) {
            awardSkillXp(player, id("mechanics"), 6, "anvil_repair", 100L, 40);
        }
    }

    /** Called by successful treatment actions; failed uses must not award XP. */
    public static boolean awardFirstAidXp(ServerPlayer player) {
        return awardSkillXp(player, id("first_aid"), 10, "successful_first_aid", 20L, 80);
    }

    /** Called only after a verified hostile kill with a supported firearm. */
    public static void awardFirearmsXp(ServerPlayer player, int amount) {
        awardSkillXp(player, id("firearms"), amount, "verified_firearm_kill", 20L, 60);
    }

    /** Called only after a verified hostile kill with a tagged melee weapon. */
    public static void awardMeleeXp(ServerPlayer player, int amount) {
        awardSkillXp(player, id("melee"), amount, "verified_melee_kill", 20L, 60);
    }

    /**
     * Mark only a block entity that still owns an unopened loot table. This happens before
     * the vanilla block interaction creates the ChestMenu and unpacks its generated loot.
     */
    public static void onLootContainerInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)) return;

        BlockEntity blockEntity = level.getBlockEntity(event.getPos());
        if (blockEntity == null) {
            PENDING_LOOT_CONTAINERS.remove(player.getUUID());
            return;
        }

        CompoundTag persistentData = blockEntity.getPersistentData();
        if (hasUnopenedLootTable(blockEntity) && !persistentData.getBoolean(LOOT_CONTAINER_MARKER)) {
            persistentData.putBoolean(LOOT_CONTAINER_MARKER, true);
            blockEntity.setChanged();
        }
        if (!persistentData.getBoolean(LOOT_CONTAINER_MARKER)) {
            PENDING_LOOT_CONTAINERS.remove(player.getUUID());
            return;
        }

        PENDING_LOOT_CONTAINERS.put(player.getUUID(), new PendingLoot(
                level.dimension().location(), event.getPos().immutable(), blockEntity,
                level.getGameTime() + 40L));
    }

    private static boolean hasUnopenedLootTable(BlockEntity blockEntity) {
        try {
            Method getter = blockEntity.getClass().getMethod("getLootTable");
            return getter.invoke(blockEntity) != null;
        } catch (NoSuchMethodException exception) {
            return false;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            AftermathMod.LOGGER.debug("Could not inspect loot table for {}: {}",
                    blockEntity.getType(), exception.getMessage());
            return false;
        }
    }

    /**
     * A chest GUI alone grants no XP. Only a ChestMenu opened immediately after interacting
     * with an ungenerated loot-table block is tracked; XP requires an actual transfer.
     */
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getContainer() instanceof ChestMenu menu)) return;

        PendingLoot pending = PENDING_LOOT_CONTAINERS.remove(player.getUUID());
        if (pending == null
                || !pending.dimension().equals(player.level().dimension().location())
                || player.level().getGameTime() > pending.expiresAt()
                || player.distanceToSqr(Vec3.atCenterOf(pending.position())) > 64.0D
                || player.level().getBlockEntity(pending.position()) != pending.blockEntity()) return;

        Container source = menu.getContainer();
        LOOTING_SESSIONS.put(player.getUUID(), new LootingSession(
                menu, source, countItems(source), countItems(player.getInventory())));
    }

    public static void onContainerClose(PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        LootingSession session = LOOTING_SESSIONS.get(player.getUUID());
        if (session != null && session.menu == event.getContainer()) {
            LOOTING_SESSIONS.remove(player.getUUID());
        }
    }

    private static void tickLooting(ServerPlayer player) {
        PendingLoot pending = PENDING_LOOT_CONTAINERS.get(player.getUUID());
        if (pending != null && (player.level().getGameTime() > pending.expiresAt()
                || !pending.dimension().equals(player.level().dimension().location()))) {
            PENDING_LOOT_CONTAINERS.remove(player.getUUID());
        }

        LootingSession session = LOOTING_SESSIONS.get(player.getUUID());
        if (session == null) return;
        if (player.containerMenu != session.menu) {
            LOOTING_SESSIONS.remove(player.getUUID());
            return;
        }

        int currentContainerItems = countItems(session.source);
        int currentInventoryItems = countItems(player.getInventory());
        session.pendingContainerLoss += Math.max(0, session.previousContainerItems - currentContainerItems);
        session.pendingInventoryGain += Math.max(0, currentInventoryItems - session.previousInventoryItems);
        session.previousContainerItems = currentContainerItems;
        session.previousInventoryItems = currentInventoryItems;

        if (session.pendingContainerLoss > 0 && session.pendingInventoryGain > 0) {
            awardSkillXp(player, id("scavenging"), 5, "looted_container_items", 600L, 60);
            int transferred = Math.min(session.pendingContainerLoss, session.pendingInventoryGain);
            session.pendingContainerLoss -= transferred;
            session.pendingInventoryGain -= transferred;
        }
    }

    private static int countItems(Container container) {
        long total = 0L;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty()) total += stack.getCount();
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    /**
     * Create machine output is credited to the nearest active player within 16 blocks.
     * A shared cooldown and daily cap keep unattended factories from becoming an XP farm.
     */
    public static void awardCreateProcessingXp(Level level, BlockPos machinePos) {
        awardNearbyMachineXp(level, machinePos, id("mechanics"), 3,
                "create_machine_processing", 100L, 80);
    }

    /** Farmer's Delight cooking pot and skillet have dedicated non-crafting output paths. */
    public static void awardFarmersDelightCookingXp(Level level, BlockPos machinePos) {
        awardNearbyMachineXp(level, machinePos, id("cooking"), 5,
                "farmers_delight_machine_cooking", 20L, 80);
    }

    private static void awardNearbyMachineXp(Level level, BlockPos machinePos, ResourceLocation skillId,
                                              int baseXp, String actionId, long cooldownTicks, int dailyCap) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        Vec3 center = Vec3.atCenterOf(machinePos);
        AABB searchArea = new AABB(machinePos).inflate(16.0D);
        ServerPlayer learner = serverLevel.getEntitiesOfClass(
                        ServerPlayer.class,
                        searchArea,
                        player -> player.isAlive() && !player.isSpectator())
                .stream()
                .min(Comparator.comparingDouble(player -> player.distanceToSqr(center)))
                .orElse(null);
        if (learner == null) return;

        awardSkillXp(learner, skillId, baseXp, actionId, cooldownTicks, dailyCap);
    }

    public static boolean awardSkillXp(ServerPlayer player, ResourceLocation skillId, int baseXp,
                                        String actionId, long cooldownTicks, int dailyCap) {
        LearningDefinitions.SkillDefinition definition =
                LearningDefinitions.snapshot().skills().get(skillId);
        if (!AftermathServerConfig.ENABLE_LEARNING.get() || baseXp <= 0
                || definition == null || !definition.progressionSource().equals("learning")) return false;

        long now = player.getServer().overworld().getGameTime();
        LearningProgress progress = player.getData(AftermathAttachments.LEARNING_PROGRESS);
        long last = progress.actionLastUsedAt(actionId);
        if (last > 0L && now >= last && now - last < cooldownTicks) return false;

        long day = Math.floorDiv(now, DAY_TICKS);
        String dailyKey = day + ":" + actionId;
        String skillDailyKey = skillDailyKeyForDay(day, skillId);
        int remainingActionDaily = Math.max(0, dailyCap - progress.actionDailyXp(dailyKey));
        int remainingSkillDaily = Math.max(0,
                SKILL_DAILY_XP_CAP - progress.actionDailyXp(skillDailyKey));
        if (remainingActionDaily <= 0 || remainingSkillDaily <= 0) return false;

        int requested = Math.max(1, (int) Math.round(baseXp * learningMultiplier(player, skillId, now)));
        int gained = Math.min(requested, Math.min(remainingActionDaily, remainingSkillDaily));
        if (gained <= 0) return false;
        LearningProgress updated = progress.addActionExperience(
                skillId.toString(), gained, actionId, now, dailyKey, skillDailyKey);
        player.setData(AftermathAttachments.LEARNING_PROGRESS, updated);

        LearningDefinitions.SkillDefinition skill = LearningDefinitions.snapshot().skills().get(skillId);
        message(player, "message.aftermath.learning.skill_xp",
                Component.translatable(skill.nameKey()), gained,
                updated.experienceFor(skillId.toString()), skillLevel(player, skillId));
        return true;
    }

    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Session session = ACTIVE_SESSIONS.get(player.getUUID());
        if (session == null || !session.cancelOnDamage()) return;
        cancel(player, "message.aftermath.learning.cancelled_damage");
    }

    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE_SESSIONS.remove(event.getEntity().getUUID());
        DRIVING_SAMPLES.remove(event.getEntity().getUUID());
        LOOTING_SESSIONS.remove(event.getEntity().getUUID());
        PENDING_LOOT_CONTAINERS.remove(event.getEntity().getUUID());
    }

    public static int skillLevel(ServerPlayer player, ResourceLocation skillId) {
        LearningDefinitions.SkillDefinition definition = LearningDefinitions.snapshot().skills().get(skillId);
        if (definition == null) return 1;
        if (definition.progressionSource().equals("weapon")) {
            int xp = player.getData(AftermathAttachments.WEAPON_PROGRESS)
                    .experienceFor(definition.progressionKey());
            return WeaponProgressionService.levelFor(xp);
        }
        LearningProgress progress = player.getData(AftermathAttachments.LEARNING_PROGRESS);
        int experienceLevel = definition.levelFor(progress.experienceFor(skillId.toString()));
        CharacterProfile profile = player.getData(AftermathAttachments.CHARACTER_PROFILE);
        int startingLevel = profile.skillLevels().getOrDefault(skillId.getPath(), 1);
        return Math.max(1, Math.min(definition.maxLevel(), Math.max(experienceLevel, startingLevel)));
    }

    public static Component buildStatusMessage(ServerPlayer player) {
        LearningProgress progress = player.getData(AftermathAttachments.LEARNING_PROGRESS);
        Component[] statuses = SKILL_STATUS_ORDER.stream().map(skillPath -> {
            ResourceLocation skillId = id(skillPath);
            LearningDefinitions.SkillDefinition definition =
                    LearningDefinitions.snapshot().skills().get(skillId);
            Component skillName = definition == null
                    ? Component.literal(skillPath)
                    : Component.translatable(definition.nameKey());
            return Component.translatable("message.aftermath.learning.skill_status",
                    skillName, skillLevel(player, skillId), progress.experienceFor(skillId.toString()));
        }).toArray(Component[]::new);
        return Component.translatable("message.aftermath.learning.status", (Object[]) statuses);
    }

    private static void complete(ServerPlayer player, Session session) {
        ACTIVE_SESSIONS.remove(player.getUUID());
        long now = player.getServer().overworld().getGameTime();
        LearningProgress progress = player.getData(AftermathAttachments.LEARNING_PROGRESS);

        if (session.type() == SessionType.BOOK) {
            LearningProgress updated = progress.activateBook(
                    session.skillId().toString(), session.bookMultiplier(), now + session.buffDurationTicks());
            player.setData(AftermathAttachments.LEARNING_PROGRESS, updated);
            message(player, "message.aftermath.learning.book_activated",
                    Component.translatable(session.titleKey()), formatMultiplier(session.bookMultiplier()),
                    seconds(session.buffDurationTicks()));
            return;
        }

        if (session.type() == SessionType.READING) {
            int currentCompletions = progress.lessonCompletionsFor(session.definitionId().toString());
            if (currentCompletions >= session.maxCompletions()) {
                message(player, "message.aftermath.learning.already_claimed");
                return;
            }
            int requestedReward = Math.max(0, (int) Math.round(
                    session.baseXpReward() * learningMultiplier(player, session.skillId(), now)));
            int reward = capStudyReward(progress, session.skillId(), requestedReward, now);
            LearningProgress updated = progress.completeLesson(
                    session.definitionId().toString(), session.skillId().toString(), reward);
            updated = updated.addDailySkillExperience(skillDailyKey(now, session.skillId()), reward);
            player.setData(AftermathAttachments.LEARNING_PROGRESS, updated);
            message(player, "message.aftermath.learning.completed",
                    Component.translatable(session.titleKey()), reward,
                    updated.experienceFor(session.skillId().toString()));
            return;
        }
        int currentCompletions = progress.programCompletionsFor(session.definitionId().toString());
        LearningDefinitions.TvProgramDefinition program =
                LearningDefinitions.snapshot().programs().get(session.definitionId());
        if (program == null || !progress.canClaimProgram(session.definitionId().toString(),
                session.maxCompletions(), now, program.repeatAfterDays())) {
            message(player, "message.aftermath.learning.already_claimed");
            return;
        }

        int requestedReward = Math.max(0, (int) Math.round(
                session.baseXpReward() * learningMultiplier(player, session.skillId(), now)));
        int reward = capStudyReward(progress, session.skillId(), requestedReward, now);
        LearningProgress updated = progress.completeProgram(
                session.definitionId().toString(), session.skillId().toString(), reward, now);
        updated = updated.addDailySkillExperience(skillDailyKey(now, session.skillId()), reward);
        player.setData(AftermathAttachments.LEARNING_PROGRESS, updated);
        if (session.type() == SessionType.VHS && session.consumeSourceItem()) {
            ResourceLocation tapeId = ResourceLocation.tryParse(session.sourceDataValue());
            if (session.televisionPos() != null
                    && player.level().getBlockEntity(session.televisionPos())
                    instanceof TrainingTelevisionBlockEntity television) {
                television.consumeCassette(session.sourceItemId(), tapeId);
            }
        }
        message(player, "message.aftermath.learning.completed",
                Component.translatable(session.titleKey()), reward,
                updated.experienceFor(session.skillId().toString()));
    }

    private static int capStudyReward(LearningProgress progress, ResourceLocation skillId,
                                      int requestedReward, long now) {
        String dailyKey = skillDailyKey(now, skillId);
        int remaining = Math.max(0, SKILL_DAILY_XP_CAP - progress.actionDailyXp(dailyKey));
        return Math.min(Math.max(0, requestedReward), remaining);
    }

    private static String skillDailyKey(long now, ResourceLocation skillId) {
        return skillDailyKeyForDay(Math.floorDiv(now, DAY_TICKS), skillId);
    }

    private static String skillDailyKeyForDay(long day, ResourceLocation skillId) {
        return day + ":skill:" + skillId;
    }

    private static double learningMultiplier(ServerPlayer player, ResourceLocation skillId, long now) {
        CharacterProfile profile = player.getData(AftermathAttachments.CHARACTER_PROFILE);
        LearningProgress progress = player.getData(AftermathAttachments.LEARNING_PROGRESS);
        double multiplier = progress.bookMultiplierFor(skillId.toString(), now);
        for (String rawId : profile.traitIds()) {
            ResourceLocation traitId = ResourceLocation.tryParse(rawId);
            if (traitId == null) continue;
            CharacterDefinitions.TraitDefinition trait =
                    CharacterDefinitions.snapshot().traits().get(traitId);
            if (trait == null) continue;
            for (CharacterDefinitions.TraitEffect effect : trait.effects()) {
                if (effect.type().equals("learning_xp_multiplier")) multiplier *= effect.value();
                if (effect.type().equals("skill_xp_multiplier")
                        && effect.target().equals(skillId.toString())) multiplier *= effect.value();
            }
        }
        return Math.max(0.25D, Math.min(15.0D, multiplier));
    }

    private static int formatMultiplier(double multiplier) {
        return (int) Math.round(multiplier);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, path);
    }

    private static void cancel(ServerPlayer player, String key) {
        if (ACTIVE_SESSIONS.remove(player.getUUID()) != null) message(player, key);
    }

    private static void message(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args), true);
    }

    private static int seconds(int ticks) {
        return (int) Math.ceil(Math.max(0, ticks) / 20.0D);
    }

    private enum SessionType { READING, BOOK, TELEVISION, VHS }

    private record Session(
            SessionType type,
            ResourceLocation definitionId,
            String titleKey,
            ResourceLocation skillId,
            ResourceLocation sourceItemId,
            InteractionHand hand,
            net.minecraft.core.BlockPos televisionPos,
            ResourceLocation dimension,
            net.minecraft.world.phys.Vec3 startPosition,
            long startedAt,
            int durationTicks,
            int baseXpReward,
            int maxCompletions,
            boolean cancelOnMovement,
            boolean cancelOnDamage,
            double bookMultiplier,
            int buffDurationTicks,
            boolean consumeSourceItem,
            String sourceDataKey,
            String sourceDataValue) {}

    private record DrivingSample(net.minecraft.world.phys.Vec3 position, long sampledAt) {}
    private record PendingLoot(ResourceLocation dimension, BlockPos position,
                               BlockEntity blockEntity, long expiresAt) {}

    private static final class LootingSession {
        private final ChestMenu menu;
        private final Container source;
        private int previousContainerItems;
        private int previousInventoryItems;
        private int pendingContainerLoss;
        private int pendingInventoryGain;

        private LootingSession(ChestMenu menu, Container source,
                               int previousContainerItems, int previousInventoryItems) {
            this.menu = menu;
            this.source = source;
            this.previousContainerItems = previousContainerItems;
            this.previousInventoryItems = previousInventoryItems;
        }
    }

    private LearningService() {}
}