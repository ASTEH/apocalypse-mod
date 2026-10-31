package dev.aftermath.world;

import dev.aftermath.AftermathMod;
import dev.aftermath.config.AftermathServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.ArrayList;
import java.util.List;

/** Server-side spawn filtering and safe removal of configured spawner blocks. */
public final class WorldSpawnControlService {
    private WorldSpawnControlService() {}

    /**
     * PositionCheck is reached by natural spawning and block spawners. FAIL rejects the spawn attempt
     * without interfering with commands, pre-existing entities or client-side rendering.
     */
    public static void onMobPositionCheck(MobSpawnEvent.PositionCheck event) {
        if (!AftermathServerConfig.ENABLE_HOSTILE_SPAWN_FILTER.get()) return;
        Mob mob = event.getEntity();
        WorldSpawnDefinitions.Snapshot policy = WorldSpawnDefinitions.snapshot();
        if (policy.shouldBlockSpawn(mob, mob.level().dimension().location())) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }

    /**
     * ChunkEvent.Load can fire before a chunk reaches FULL status. Queue the block edits through
     * MinecraftServer#tell so all level interactions happen on the server thread after load finishes.
     */
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!AftermathServerConfig.ENABLE_SPAWNER_REMOVAL.get()) return;
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getChunk() instanceof LevelChunk chunk)) return;

        WorldSpawnDefinitions.Snapshot policy = WorldSpawnDefinitions.snapshot();
        if (!policy.shouldRemoveSpawners(level.dimension().location())) return;

        ChunkPos chunkPos = chunk.getPos();
        level.getServer().tell(() -> removeSpawnerBlocks(level, chunkPos));
    }

    private static void removeSpawnerBlocks(ServerLevel level, ChunkPos chunkPos) {
        if (!AftermathServerConfig.ENABLE_SPAWNER_REMOVAL.get()) return;
        if (!level.getChunkSource().hasChunk(chunkPos.x, chunkPos.z)) return;

        LevelChunk loadedChunk = level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
        if (loadedChunk == null) return;

        WorldSpawnDefinitions.Snapshot policy = WorldSpawnDefinitions.snapshot();
        if (!policy.shouldRemoveSpawners(level.dimension().location())) return;

        int removed = 0;
        List<BlockPos> blockEntityPositions = new ArrayList<>(loadedChunk.getBlockEntitiesPos());
        for (BlockPos storedPos : blockEntityPositions) {
            BlockPos pos = storedPos.immutable();
            if (!level.hasChunkAt(pos)) continue;

            var state = level.getBlockState(pos);
            var blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (!policy.spawnerBlockIds().contains(blockId)) continue;

            if (level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3)) {
                removed++;
            }
        }

        if (removed > 0) {
            AftermathMod.LOGGER.info("Removed {} configured spawner block(s) from loaded chunk {} in {}.",
                    removed, chunkPos, level.dimension().location());
        }
    }
}
