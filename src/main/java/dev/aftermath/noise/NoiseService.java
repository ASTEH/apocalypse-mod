package dev.aftermath.noise;
import dev.aftermath.AftermathMod;
import dev.aftermath.config.AftermathServerConfig;
import dev.aftermath.day.DayProgressionDefinitions;
import dev.aftermath.day.WorldProgressData;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Server-side noise API for optional weapon adapters. */
public final class NoiseService {
    private static final Map<CooldownKey, Long> LAST_EMITTED = new HashMap<>();
    private static final long CLEANUP_AGE = 20L * 60L * 10L;
    private static final ResourceLocation GUNSHOT = ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "gunshot");
    private static final ResourceLocation CHAT = eventId("chat");
    private static final ResourceLocation BLOCK_ACTION = eventId("block_action");
    private static final ResourceLocation QUIET_SOUND = eventId("sound_quiet");
    private static final ResourceLocation MEDIUM_SOUND = eventId("sound_medium");
    private static final ResourceLocation LOUD_SOUND = eventId("sound_loud");
    private static final ResourceLocation WALK_FOOTSTEPS = eventId("footsteps");
    private static final ResourceLocation SPRINT_FOOTSTEPS = eventId("sprint_footsteps");
    private static final ResourceLocation CLUMSY_FOOTSTEPS = eventId("clumsy_footsteps");
    private static final ResourceLocation CLUMSY_SPRINT = eventId("clumsy_sprint");
    private static final Map<UUID, Long> LAST_MOVEMENT_NOISE = new HashMap<>();
    private static final Map<ResourceLocation, SoundScanBudget> SOUND_SCAN_BUDGETS = new HashMap<>();
    /** Call after the adapter has verified a sound-producing action. Null source is allowed for world-originated events. */
    public static NoiseResult emitNoise(ServerLevel level, Vec3 origin, ResourceLocation eventId, UUID sourceId) {
        if (!AftermathServerConfig.ENABLE_NOISE_EVENTS.get()) return new NoiseResult(Status.DISABLED, 0, 0);
        NoiseDefinitions.NoiseEventDefinition def = NoiseDefinitions.snapshot().noiseEvents().get(eventId);
        if (def == null) return new NoiseResult(Status.UNKNOWN_EVENT, 0, 0);
        long now = level.getGameTime();
        ResourceLocation dim = level.dimension().location();
        CooldownKey key = new CooldownKey(dim, eventId, sourceId);
        Long previous = LAST_EMITTED.get(key);
        if (previous != null && now >= previous && now - previous < def.cooldownTicks())
            return new NoiseResult(Status.COOLDOWN, 0, 0);
        LAST_EMITTED.put(key, now);
        int baseRadius = def.noiseRadius() == 0
                ? AftermathServerConfig.DEFAULT_NOISE_RADIUS.get() : def.noiseRadius();
        int day = WorldProgressData.currentDay(level.getServer());
        DayProgressionDefinitions.Stage dayStage = DayProgressionDefinitions.stageForDay(day);
        double detectionMultiplier = AftermathServerConfig.ENABLE_DAY_PROGRESSION.get()
                ? dayStage.detectionMultiplier() : 1.0D;
        int radius = (int) Math.ceil(baseRadius * detectionMultiplier);
        radius = Math.max(0, Math.min(512, radius));
        int stageGroupLimit = AftermathServerConfig.ENABLE_DAY_PROGRESSION.get()
                ? dayStage.groupSize() : AftermathServerConfig.MAX_HORDE_SIZE.get();
        if (radius == 0) return new NoiseResult(Status.EMITTED, 0, 0);
        AABB bounds = new AABB(origin.x - radius, origin.y - radius, origin.z - radius,
                origin.x + radius, origin.y + radius, origin.z + radius);
        TagKey<EntityType<?>> tag = TagKey.create(Registries.ENTITY_TYPE, def.targetTag());
        // This is a loaded-entity spatial query and never requests a chunk to be loaded.
        ArrayList<Mob> mobs = new ArrayList<>(level.getEntitiesOfClass(Mob.class, bounds,
                mob -> ZombieEntityClassifier.isEligibleForNoise(mob, tag) && mob.isAlive()
                        && !mob.isRemoved() && !mob.isNoAi()));
        double r2 = (double) radius * radius;
        mobs.removeIf(mob -> mob.position().distanceToSqr(origin) > r2);
        mobs.sort(Comparator.comparingDouble(mob -> mob.position().distanceToSqr(origin)));
        int maxTargets = Math.min(def.maxAffectedEntities(), AftermathServerConfig.MAX_NOISE_TARGETS.get());
        if (mobs.size() > maxTargets) mobs.subList(maxTargets, mobs.size()).clear();

        NoiseDefinitions.HordeArchetypeDefinition archetype = NoiseDefinitions.snapshot().hordeArchetypes().values()
                .stream().filter(a -> a.leader() && a.entityTag().equals(def.targetTag()))
                .min(Comparator.comparing(a -> a.id().toString())).orElse(null);
        int followers = HordeDirector.assignNoise(level, origin, mobs, def.lureDurationTicks(),
                HordeDirector.isHordeBehaviorEnabled(), stageGroupLimit, archetype);
        AftermathMod.LOGGER.debug("Noise {} at {} in {} affected {} loaded mobs; followers={}; day={}, radius={}.",
                eventId, origin, dim, mobs.size(), followers, day, radius);
        if (LAST_EMITTED.size() > 4096) LAST_EMITTED.entrySet().removeIf(e -> {
            long stamp = e.getValue();
            return now < stamp || (e.getKey().dimension().equals(dim) && now - stamp >= CLEANUP_AGE);
        });
        return new NoiseResult(Status.EMITTED, mobs.size(), followers);
    }
    private static ResourceLocation eventId(String path) {
        return ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, path);
    }

    /**
     * Converts server-emitted sounds attached to entities into world noise. The originating entity
     * is retained as the cooldown key, so different players/entities do not silence each other.
     */
    public static void onLevelSoundAtEntity(PlayLevelSoundEvent.AtEntity event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !AftermathServerConfig.ENABLE_NOISE_EVENTS.get()
                || event.getSound() == null
                || event.getNewVolume() <= 0.05F) return;

        Entity source = event.getEntity();
        ResourceLocation soundId = event.getSound().unwrapKey().map(key -> key.location()).orElse(null);
        if (soundId == null) return;
        String soundPath = soundId.getPath().toLowerCase(Locale.ROOT);
        if (source instanceof ServerPlayer && isFootstepSound(soundPath)) return;
        if (source instanceof Mob mob && ZombieEntityClassifier.isZombieLike(mob)) return;
        if (isTaCZGunshot(soundId, soundPath)) return;

        ResourceLocation noiseType = classifySound(soundId, event.getSource(), event.getNewVolume());
        if (noiseType != null && consumeSoundScan(level)) {
            emitNoise(level, source.position(), noiseType, source.getUUID());
        }
    }

    /**
     * Captures block/world sounds which are emitted at a position rather than an entity. A stable
     * position-based key prevents a continuously sounding block from rescanning mobs every tick.
     */
    public static void onLevelSoundAtPosition(PlayLevelSoundEvent.AtPosition event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !AftermathServerConfig.ENABLE_NOISE_EVENTS.get()
                || event.getSound() == null
                || event.getNewVolume() <= 0.05F) return;
        if (isIgnoredSoundCategory(event.getSource())) return;

        ResourceLocation soundId = event.getSound().unwrapKey().map(key -> key.location()).orElse(null);
        if (soundId == null) return;
        String path = soundId.getPath().toLowerCase(Locale.ROOT);
        if (isTaCZGunshot(soundId, path)) return;

        ResourceLocation noiseType = classifySound(soundId, event.getSource(), event.getNewVolume());
        if (noiseType == null) return;
        Vec3 position = event.getPosition();
        if (!consumeSoundScan(level)) return;
        emitNoise(level, position, noiseType, positionSourceId(level, position, noiseType));
    }

    /** Plain chat is treated as talking; slash commands never emit a chat-noise event. */
    public static void onServerChat(ServerChatEvent event) {
        if (!(event.getPlayer().level() instanceof ServerLevel level)
                || !AftermathServerConfig.ENABLE_NOISE_EVENTS.get()) return;
        String text = event.getRawText();
        if (text == null || text.isBlank() || text.stripLeading().startsWith("/")) return;
        if (consumeSoundScan(level)) {
            emitNoise(level, event.getPlayer().position(), CHAT, event.getPlayer().getUUID());
        }
    }

    /** Fallback for openable/usable blocks even when a mod doesn't emit a standard block sound. */
    public static void onBlockInteraction(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)
                || !AftermathServerConfig.ENABLE_NOISE_EVENTS.get()) return;
        BlockState state = level.getBlockState(event.getPos());
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id == null) return;
        String path = id.getPath().toLowerCase(Locale.ROOT);
        if (containsAny(path, "door", "trapdoor", "fence_gate", "chest", "barrel",
                "shulker_box", "button", "lever", "dispenser", "dropper", "furnace",
                "smoker", "blast_furnace", "lectern", "beehive")) {
            emitNoise(level, Vec3.atCenterOf(event.getPos()), BLOCK_ACTION,
                    positionSourceId(level, Vec3.atCenterOf(event.getPos()), BLOCK_ACTION));
        }
    }

    /** Block breaks are noisy even when a block/mod doesn't emit a server-visible sound event. */
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getPlayer() instanceof ServerPlayer player)
                || !AftermathServerConfig.ENABLE_NOISE_EVENTS.get()
                || !consumeSoundScan(level)) return;
        Vec3 origin = Vec3.atCenterOf(event.getPos());
        emitNoise(level, origin, BLOCK_ACTION, positionSourceId(level, origin, BLOCK_ACTION));
    }

    /** Placements are quieter than breaking, but still audible to nearby infected. */
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof ServerPlayer)
                || !AftermathServerConfig.ENABLE_NOISE_EVENTS.get()
                || !consumeSoundScan(level)) return;
        Vec3 origin = Vec3.atCenterOf(event.getPos());
        emitNoise(level, origin, MEDIUM_SOUND, positionSourceId(level, origin, MEDIUM_SOUND));
    }

    /** Walking is quieter; sprinting is louder and emits more often. Sneaking is silent by default. */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)
                || !AftermathServerConfig.ENABLE_NOISE_EVENTS.get()
                || player.isRemoved() || player.isSpectator() || player.isPassenger()
                || !player.onGround()
                || player.getDeltaMovement().horizontalDistanceSqr() <= 0.0025D) return;

        boolean clumsy = dev.aftermath.character.TraitRuntimeService.hasHook(player, "clumsy_noise");
        if (player.isCrouching() && !clumsy) return;

        boolean sprinting = player.isSprinting();
        long interval = sprinting ? 5L : 9L;
        long now = level.getGameTime();
        Long previous = LAST_MOVEMENT_NOISE.get(player.getUUID());
        if (previous != null && now >= previous && now - previous < interval) return;
        if (!consumeSoundScan(level)) return;
        LAST_MOVEMENT_NOISE.put(player.getUUID(), now);

        ResourceLocation eventId;
        if (sprinting) eventId = clumsy ? CLUMSY_SPRINT : SPRINT_FOOTSTEPS;
        else if (clumsy) eventId = CLUMSY_FOOTSTEPS;
        else eventId = WALK_FOOTSTEPS;
        emitNoise(level, player.position(), eventId, player.getUUID());
    }

    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_MOVEMENT_NOISE.remove(event.getEntity().getUUID());
    }

    private static UUID positionSourceId(ServerLevel level, Vec3 position, ResourceLocation eventType) {
        String anchor = level.dimension().location() + ":" + Math.floor(position.x) + ":"
                + Math.floor(position.y) + ":" + Math.floor(position.z) + ":" + eventType;
        return UUID.nameUUIDFromBytes(anchor.getBytes(StandardCharsets.UTF_8));
    }

    /** Bound generic sound-driven spatial queries to a configurable budget per dimension/tick. */
    private static boolean consumeSoundScan(ServerLevel level) {
        ResourceLocation dimension = level.dimension().location();
        long tick = level.getGameTime();
        int configuredBudget = AftermathServerConfig.MAX_SOUND_NOISE_SCANS_PER_TICK.get();
        SoundScanBudget current = SOUND_SCAN_BUDGETS.get(dimension);
        if (current == null || current.tick() != tick) {
            current = new SoundScanBudget(tick, configuredBudget);
        }
        if (current.remaining() <= 0) {
            SOUND_SCAN_BUDGETS.put(dimension, current);
            return false;
        }
        SOUND_SCAN_BUDGETS.put(dimension, new SoundScanBudget(tick, current.remaining() - 1));
        return true;
    }

    private static ResourceLocation classifySound(ResourceLocation soundId, SoundSource source, float volume) {
        if (isIgnoredSoundCategory(source)) return null;
        String id = (soundId.getNamespace() + ":" + soundId.getPath()).toLowerCase(Locale.ROOT);
        if (containsAny(id, "door", "trapdoor", "fence_gate", "chest", "barrel",
                "shulker_box", "dispenser", "dropper", "lever", "button", "pressure_plate")) {
            return BLOCK_ACTION;
        }
        if (containsAny(id, "explosion", "explode", "tnt", "firework", "thunder",
                "sonic_boom", "warden", "anvil", "bell", "cannon", "gunshot", "shotgun_blast")) {
            return LOUD_SOUND;
        }
        if (volume >= 1.45F) return LOUD_SOUND;
        return volume < 0.45F ? QUIET_SOUND : MEDIUM_SOUND;
    }

    private static boolean isIgnoredSoundCategory(SoundSource source) {
        return source == SoundSource.MASTER
                || source == SoundSource.MUSIC
                || source == SoundSource.RECORDS
                || source == SoundSource.WEATHER
                || source == SoundSource.AMBIENT;
    }

    private static boolean isFootstepSound(String path) {
        return path.contains("step") || path.contains("footstep");
    }

    private static boolean isTaCZGunshot(ResourceLocation id, String path) {
        if (!"tacz".equals(id.getNamespace())) return false;
        return containsAny(path, "shoot", "shooting", "gunshot", "fire_bullet", "bullet_fire");
    }

    private static boolean containsAny(String value, String... tokens) {
        for (String token : tokens) {
            if (value.contains(token)) return true;
        }
        return false;
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % 200L != 0L) return;
        long now = level.getGameTime();
        ResourceLocation dim = level.dimension().location();
        LAST_EMITTED.entrySet().removeIf(e -> e.getKey().dimension().equals(dim)
                && (now < e.getValue() || now - e.getValue() >= CLEANUP_AGE));
        SOUND_SCAN_BUDGETS.entrySet().removeIf(e -> e.getKey().equals(dim)
                && now > e.getValue().tick() + 1L);
        HordeDirector.cleanupLevel(level);
    }
    public static void onServerStopping(ServerStoppingEvent event) {
        LAST_EMITTED.clear();
        LAST_MOVEMENT_NOISE.clear();
        SOUND_SCAN_BUDGETS.clear();
    }
    public static ResourceLocation gunshotEventId() { return GUNSHOT; }
    public enum Status { EMITTED, DISABLED, UNKNOWN_EVENT, COOLDOWN }
    public record NoiseResult(Status status, int affectedEntities, int groupedFollowers) {}
    private record CooldownKey(ResourceLocation dimension, ResourceLocation eventId, UUID sourceId) {}
    private record SoundScanBudget(long tick, int remaining) {}
    private NoiseService() {}
}
