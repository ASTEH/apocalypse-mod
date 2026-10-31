package dev.aftermath.foraging;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.aftermath.AftermathMod;
import dev.aftermath.character.AftermathAttachments;
import dev.aftermath.character.CharacterProfile;
import dev.aftermath.config.AftermathServerConfig;
import dev.aftermath.learning.LearningService;
import dev.aftermath.network.ForagingSnapshotPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-authoritative, data-driven scavenging. Search cells are deterministic and queried only
 * around active players. No hidden entities are spawned and no unloaded chunks are requested.
 */
public final class ForagingService {
    private static final int CELL_SIZE = 5;
    private static final int MAX_VISIBLE_FINDS = 48;
    private static final double EYE_DISTANCE = 6.0D;
    private static final double REVEAL_DISTANCE = 3.0D;
    private static final double PICKUP_DISTANCE = 3.5D;
    private static final double DARK_SEARCH_RADIUS = 1.5D;
    private static final Set<UUID> SEARCHING = new HashSet<>();
    private static final Map<UUID, String> FOCUSES = new HashMap<>();
    private static final Map<UUID, Map<Long, VisibleFind>> SERVER_FINDS = new HashMap<>();
    private static final Map<UUID, Long> LAST_FOCUS_CHANGE = new HashMap<>();
    private static final Map<UUID, Long> LAST_PICKUP = new HashMap<>();

    private ForagingService() {}

    public static void toggleSearch(ServerPlayer player) {
        UUID id = player.getUUID();
        if (!AftermathServerConfig.ENABLE_FORAGING.get()) {
            player.sendSystemMessage(Component.translatable("message.aftermath.foraging.disabled"));
            return;
        }
        if (!SEARCHING.remove(id)) {
            if (ForagingDefinitions.snapshot().categories().isEmpty()) {
                player.sendSystemMessage(Component.translatable("message.aftermath.foraging.data_unavailable"));
                return;
            }
            SEARCHING.add(id);
            String initial = ForagingDefinitions.snapshot().categories().containsKey("food")
                    ? "food" : ForagingDefinitions.snapshot().categoryIds().get(0);
            FOCUSES.put(id, initial);
            player.sendSystemMessage(Component.translatable("message.aftermath.foraging.started"));
            scanAndSend(player);
        } else {
            FOCUSES.remove(id);
            SERVER_FINDS.remove(id);
            sendInactive(player);
            player.sendSystemMessage(Component.translatable("message.aftermath.foraging.stopped"));
        }
    }

    public static void setFocus(ServerPlayer player, String categoryId) {
        UUID id = player.getUUID();
        if (!SEARCHING.contains(id) || categoryId == null
                || !categoryId.matches("[a-z0-9_-]{1,32}")
                || !ForagingDefinitions.snapshot().categories().containsKey(categoryId)) return;
        long now = player.serverLevel().getGameTime();
        long previous = LAST_FOCUS_CHANGE.getOrDefault(id, Long.MIN_VALUE);
        if (previous != Long.MIN_VALUE && now - previous < 5L) return;
        LAST_FOCUS_CHANGE.put(id, now);
        FOCUSES.put(id, categoryId);
        scanAndSend(player);
    }

    public static void collect(ServerPlayer player, long packedPosition) {
        UUID id = player.getUUID();
        if (!SEARCHING.contains(id)) return;
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        long previous = LAST_PICKUP.getOrDefault(id, Long.MIN_VALUE);
        if (previous != Long.MIN_VALUE && now - previous < 5L) return;

        Map<Long, VisibleFind> knownFinds = SERVER_FINDS.get(id);
        VisibleFind known = knownFinds == null ? null : knownFinds.get(packedPosition);
        if (known == null || !known.revealed() || !known.dimension().equals(level.dimension().location())) {
            player.sendSystemMessage(Component.translatable("message.aftermath.foraging.nothing_close"));
            return;
        }

        BlockPos pos = BlockPos.of(packedPosition);
        double dx = player.getX() - (pos.getX() + 0.5D);
        double dz = player.getZ() - (pos.getZ() + 0.5D);
        double dy = player.getY() - pos.getY();
        if (dx * dx + dz * dz > PICKUP_DISTANCE * PICKUP_DISTANCE
                || Math.abs(dy) > 3.0D || !level.hasChunkAt(pos)) {
            player.sendSystemMessage(Component.translatable("message.aftermath.foraging.nothing_close"));
            return;
        }

        ForagingWorldData data = ForagingWorldData.get(level);
        if (data.isDepleted(pos, now)) {
            player.sendSystemMessage(Component.translatable("message.aftermath.foraging.nothing_close"));
            scanAndSend(player);
            return;
        }
        ForagingWorldData.StoredFind stored = data.findAt(pos);
        if (stored == null) {
            player.sendSystemMessage(Component.translatable("message.aftermath.foraging.nothing_close"));
            scanAndSend(player);
            return;
        }

        Item item = BuiltInRegistries.ITEM.get(stored.itemId());
        if (item == Items.AIR) {
            data.markDepleted(pos, now);
            scanAndSend(player);
            return;
        }

        ItemStack stack = new ItemStack(item, Math.min(stored.count(), item.getMaxStackSize()));
        Component itemName = stack.getHoverName();
        int count = stack.getCount();
        player.getInventory().add(stack);
        if (!stack.isEmpty()) player.drop(stack, false);
        data.markDepleted(pos, now);
        LAST_PICKUP.put(id, now);

        LearningService.awardSkillXp(player,
                ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "scavenging"),
                2, "foraging_collection", 20L, 40);
        player.sendSystemMessage(Component.translatable("message.aftermath.foraging.collected", itemName, count));
        scanAndSend(player);
    }

    /** Records rainfall per dimension even when nobody is actively searching. */
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % 200L != 0L) return;
        if (level.isRaining()) ForagingWorldData.get(level).observeRain(level.getGameTime());
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !SEARCHING.contains(player.getUUID())) return;
        if (!AftermathServerConfig.ENABLE_FORAGING.get()) {
            stopSearch(player);
            return;
        }
        int interval = AftermathServerConfig.FORAGING_SCAN_INTERVAL_TICKS.get();
        if (player.tickCount % interval == 0) scanAndSend(player);
    }

    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        clearPlayer(event.getEntity().getUUID());
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        SEARCHING.clear();
        FOCUSES.clear();
        SERVER_FINDS.clear();
        LAST_FOCUS_CHANGE.clear();
        LAST_PICKUP.clear();
    }

    private static void stopSearch(ServerPlayer player) {
        clearPlayer(player.getUUID());
        sendInactive(player);
    }

    private static void clearPlayer(UUID id) {
        SEARCHING.remove(id);
        FOCUSES.remove(id);
        SERVER_FINDS.remove(id);
        LAST_FOCUS_CHANGE.remove(id);
        LAST_PICKUP.remove(id);
    }

    private static void scanAndSend(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        UUID playerId = player.getUUID();
        long now = level.getGameTime();
        ForagingWorldData worldData = ForagingWorldData.get(level);
        if (level.isRainingAt(player.blockPosition())) worldData.observeRain(now);

        String focus = FOCUSES.getOrDefault(playerId, "food");
        if (!ForagingDefinitions.snapshot().categories().containsKey(focus)) {
            focus = ForagingDefinitions.snapshot().categoryIds().stream().findFirst().orElse("");
            FOCUSES.put(playerId, focus);
        }
        double radius = effectiveRadius(player, level);
        boolean wet = level.isRainingAt(player.blockPosition()) || worldData.recentlyWet(now);
        long dayTime = Math.floorMod(level.getDayTime(), 24_000L);
        boolean night = dayTime >= 13_000L && dayTime < 23_000L;
        int skillLevel = scavengingLevel(player);

        int minCellX = Math.floorDiv(player.getBlockX() - (int) Math.ceil(radius), CELL_SIZE);
        int maxCellX = Math.floorDiv(player.getBlockX() + (int) Math.ceil(radius), CELL_SIZE);
        int minCellZ = Math.floorDiv(player.getBlockZ() - (int) Math.ceil(radius), CELL_SIZE);
        int maxCellZ = Math.floorDiv(player.getBlockZ() + (int) Math.ceil(radius), CELL_SIZE);

        List<VisibleFind> visible = new ArrayList<>();
        Map<Long, VisibleFind> validated = new LinkedHashMap<>();
        double radiusSquared = radius * radius;
        double focusMultiplier = AftermathServerConfig.FORAGING_FOCUS_WEIGHT_MULTIPLIER.get();
        double professionLootBonus = isScavenger(player) ? 1.2D : 1.0D;

        for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
            for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                long seed = cellSeed(level, cellX, cellZ);
                RandomSource cellRandom = RandomSource.create(seed);
                if (cellRandom.nextDouble() >= AftermathServerConfig.FORAGING_SPOT_CHANCE.get()) continue;

                int x = cellX * CELL_SIZE + cellRandom.nextInt(CELL_SIZE);
                int z = cellZ * CELL_SIZE + cellRandom.nextInt(CELL_SIZE);
                double dx = (x + 0.5D) - player.getX();
                double dz = (z + 0.5D) - player.getZ();
                double distanceSquared = dx * dx + dz * dz;
                if (distanceSquared > radiusSquared) continue;

                BlockPos probe = new BlockPos(x, player.getBlockY(), z);
                if (!level.hasChunkAt(probe)) continue;
                BlockPos pos = findGroundPosition(level, x, player.getBlockY(), z);
                if (pos == null) continue;
                if (worldData.isDepleted(pos, now)) continue;

                ForagingWorldData.StoredFind stored = worldData.findAt(pos);
                if (stored == null) {
                    boolean winter = level.getBiome(pos).value().getBaseTemperature() <= 0.15F;
                    boolean nearFarm = isNearFarmland(level, pos);
                    boolean nearRoad = isNearRoad(level, pos);
                    boolean nearUrban = isUrbanArea(level, pos);
                    RandomSource lootRandom = RandomSource.create(mix(seed ^ pos.asLong() ^ 0x632BE59BD9B4E019L));
                    ForagingDefinitions.LootChoice choice = ForagingDefinitions.chooseLoot(
                            level, pos, skillLevel, wet, night, winter,
                            nearFarm, nearRoad, nearUrban, focus,
                            focusMultiplier * professionLootBonus, lootRandom);
                    if (choice == null || BuiltInRegistries.ITEM.get(choice.itemId()) == Items.AIR) continue;
                    stored = worldData.rememberFind(pos, choice.itemId(), choice.count(), now);
                    if (stored == null) continue;
                }

                double distance = Math.sqrt(distanceSquared);
                boolean revealed = distance <= REVEAL_DISTANCE;
                VisibleFind find = new VisibleFind(pos.asLong(), pos.getX(), pos.getY(), pos.getZ(),
                        distance, revealed, stored.itemId(), stored.count(), level.dimension().location());
                validated.put(find.packedPosition(), find);
                if (distance <= EYE_DISTANCE) visible.add(find);
                if (visible.size() >= MAX_VISIBLE_FINDS) break;
            }
            if (visible.size() >= MAX_VISIBLE_FINDS) break;
        }

        visible.sort(Comparator.comparingDouble(VisibleFind::distance));
        SERVER_FINDS.put(playerId, validated);
        sendSnapshot(player, focus, radius, visible);
    }

    /** Finds an air block over a floor near the player's current Y, including caves and interiors. */
    private static BlockPos findGroundPosition(ServerLevel level, int x, int playerY, int z) {
        for (int offset = 3; offset >= -6; offset--) {
            int y = playerY + offset;
            if (y <= level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) continue;
            BlockPos candidate = new BlockPos(x, y, z);
            BlockPos ground = candidate.below();
            if (!level.hasChunkAt(candidate) || !level.hasChunkAt(ground)) continue;
            if (!level.getBlockState(candidate).isAir() || !level.getFluidState(candidate).isEmpty()) continue;
            if (level.getBlockState(ground).isAir() || !level.getFluidState(ground).isEmpty()) continue;
            return candidate;
        }
        return null;
    }

    private static int scavengingLevel(ServerPlayer player) {
        return LearningService.skillLevel(player,
                ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "scavenging"));
    }

    private static double effectiveRadius(ServerPlayer player, ServerLevel level) {
        BlockPos eye = player.blockPosition();
        if (level.getBrightness(LightLayer.BLOCK, eye) <= 1
                && level.getBrightness(LightLayer.SKY, eye) <= 1) return DARK_SEARCH_RADIUS;

        int skillLevel = scavengingLevel(player);
        double radius = Math.min(AftermathServerConfig.FORAGING_MAX_BASE_RADIUS.get(),
                AftermathServerConfig.FORAGING_BASE_RADIUS.get()
                        + Math.max(0, skillLevel - 1) * AftermathServerConfig.FORAGING_RADIUS_PER_SKILL_LEVEL.get());
        if (isScavenger(player)) radius += AftermathServerConfig.FORAGING_PROFESSION_RADIUS_BONUS.get();
        if (hasKeenEye(player)) radius += AftermathServerConfig.FORAGING_TRAIT_RADIUS_BONUS.get();
        return Math.max(3.0D, Math.min(15.0D, radius));
    }

    private static boolean isScavenger(ServerPlayer player) {
        CharacterProfile profile = player.getData(AftermathAttachments.CHARACTER_PROFILE);
        return profile.professionId().equals("aftermath:scavenger") || profile.professionId().equals("scavenger");
    }

    private static boolean hasKeenEye(ServerPlayer player) {
        CharacterProfile profile = player.getData(AftermathAttachments.CHARACTER_PROFILE);
        return profile.traitIds().stream().anyMatch(id ->
                id.equals("aftermath:scavenger_keen_eye") || id.equals("scavenger_keen_eye"));
    }

    private static boolean isNearFarmland(ServerLevel level, BlockPos pos) {
        for (int dx = -2; dx <= 2; dx += 2) {
            for (int dz = -2; dz <= 2; dz += 2) {
                BlockPos sample = pos.offset(dx, -1, dz);
                if (level.hasChunkAt(sample) && level.getBlockState(sample).is(Blocks.FARMLAND)) return true;
            }
        }
        return false;
    }

    private static boolean isNearRoad(ServerLevel level, BlockPos pos) {
        for (int dx = -2; dx <= 2; dx += 2) {
            for (int dz = -2; dz <= 2; dz += 2) {
                BlockPos sample = pos.offset(dx, -1, dz);
                if (!level.hasChunkAt(sample)) continue;
                var state = level.getBlockState(sample);
                if (state.is(Blocks.GRAVEL) || state.is(Blocks.COBBLESTONE)
                        || state.is(Blocks.STONE_BRICKS) || state.is(Blocks.POLISHED_ANDESITE)
                        || state.is(Blocks.GRAY_CONCRETE) || state.is(Blocks.BLACK_CONCRETE)
                        || state.is(Blocks.SMOOTH_STONE)) return true;
            }
        }
        return false;
    }

    private static boolean isUrbanArea(ServerLevel level, BlockPos pos) {
        int buildingBlocks = 0;
        for (int dx = -2; dx <= 2; dx += 2) {
            for (int dz = -2; dz <= 2; dz += 2) {
                BlockPos groundPos = pos.offset(dx, -1, dz);
                BlockPos nearbyPos = pos.offset(dx, 0, dz);
                boolean built = level.hasChunkAt(groundPos) && isBuiltMaterial(level.getBlockState(groundPos));
                built |= level.hasChunkAt(nearbyPos) && isBuiltMaterial(level.getBlockState(nearbyPos));
                if (built) buildingBlocks++;
            }
        }
        return buildingBlocks >= 3;
    }

    private static boolean isBuiltMaterial(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(Blocks.BRICKS) || state.is(Blocks.STONE_BRICKS)
                || state.is(Blocks.COBBLESTONE) || state.is(Blocks.OAK_PLANKS)
                || state.is(Blocks.GLASS) || state.is(Blocks.GLASS_PANE)
                || state.is(Blocks.IRON_BARS) || state.is(Blocks.GRAY_CONCRETE)
                || state.is(Blocks.BLACK_CONCRETE) || state.is(Blocks.SMOOTH_STONE);
    }

    private static long cellSeed(ServerLevel level, int cellX, int cellZ) {
        long seed = level.getSeed();
        seed ^= (long) cellX * 0x9E3779B97F4A7C15L;
        seed ^= (long) cellZ * 0xC2B2AE3D27D4EB4FL;
        seed ^= (long) level.dimension().location().hashCode() * 0x165667B19E3779F9L;
        return mix(seed);
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static void sendInactive(ServerPlayer player) {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.addProperty("active", false);
        root.addProperty("focus", "");
        root.addProperty("radius", 0.0D);
        root.add("categories", new JsonArray());
        root.add("finds", new JsonArray());
        PacketDistributor.sendToPlayer(player, new ForagingSnapshotPayload(root.toString()));
    }

    private static void sendSnapshot(ServerPlayer player, String focus, double radius, List<VisibleFind> finds) {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.addProperty("active", true);
        root.addProperty("focus", focus);
        root.addProperty("radius", radius);

        JsonArray categories = new JsonArray();
        ForagingDefinitions.snapshot().categoryIds().forEach(categories::add);
        root.add("categories", categories);

        JsonArray entries = new JsonArray();
        for (VisibleFind find : finds) {
            JsonObject item = new JsonObject();
            item.addProperty("x", find.x());
            item.addProperty("y", find.y());
            item.addProperty("z", find.z());
            item.addProperty("distance", find.distance());
            item.addProperty("revealed", find.revealed());
            if (find.revealed()) {
                item.addProperty("item", find.itemId().toString());
                item.addProperty("count", find.count());
            }
            entries.add(item);
        }
        root.add("finds", entries);
        PacketDistributor.sendToPlayer(player, new ForagingSnapshotPayload(root.toString()));
    }

    private record VisibleFind(long packedPosition, int x, int y, int z, double distance,
                               boolean revealed, ResourceLocation itemId, int count,
                               ResourceLocation dimension) {}
}
