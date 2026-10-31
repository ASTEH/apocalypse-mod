package dev.aftermath.noise;
import dev.aftermath.AftermathMod;
import dev.aftermath.config.AftermathServerConfig;
import dev.aftermath.day.DayProgressionDefinitions;
import dev.aftermath.day.WorldProgressData;
import dev.aftermath.zombie.ZombieVariantService;
import dev.aftermath.noise.compat.ZombieAwarenessCompatibility;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
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

/** Server-side noise API for optional weapon adapters. */
public final class NoiseService {
    private static final Map<CooldownKey, Long> LAST_EMITTED = new HashMap<>();
    private static final long CLEANUP_AGE = 20L * 60L * 10L;
    private static final ResourceLocation GUNSHOT = ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "gunshot");
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
        ZombieAwarenessCompatibility.emitSoundSense(level, origin, radius);
        AABB bounds = new AABB(origin.x - radius, origin.y - radius, origin.z - radius,
                origin.x + radius, origin.y + radius, origin.z + radius);
        TagKey<EntityType<?>> tag = TagKey.create(Registries.ENTITY_TYPE, def.targetTag());
        // This is a loaded-entity spatial query and never requests a chunk to be loaded.
        ArrayList<Mob> mobs = new ArrayList<>(level.getEntitiesOfClass(Mob.class, bounds,
                mob -> mob.getType().is(tag) && mob.isAlive() && !mob.isRemoved() && !mob.isNoAi()));
        final int emittedRadius = radius;
        mobs.removeIf(mob -> {
            double hearingRadius = Math.min(emittedRadius,
                    ZombieVariantService.hearingRange(mob, emittedRadius));
            return mob.position().distanceToSqr(origin) > hearingRadius * hearingRadius;
        });
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
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % 200L != 0L) return;
        long now = level.getGameTime();
        ResourceLocation dim = level.dimension().location();
        LAST_EMITTED.entrySet().removeIf(e -> e.getKey().dimension().equals(dim)
                && (now < e.getValue() || now - e.getValue() >= CLEANUP_AGE));
        HordeDirector.cleanupLevel(level);
    }
    public static void onServerStopping(ServerStoppingEvent event) { LAST_EMITTED.clear(); }
    public static ResourceLocation gunshotEventId() { return GUNSHOT; }
    public enum Status { EMITTED, DISABLED, UNKNOWN_EVENT, COOLDOWN }
    public record NoiseResult(Status status, int affectedEntities, int groupedFollowers) {}
    private record CooldownKey(ResourceLocation dimension, ResourceLocation eventId, UUID sourceId) {}
    private NoiseService() {}
}
