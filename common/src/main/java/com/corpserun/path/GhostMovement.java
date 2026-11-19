package com.corpserun.path;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

/**
 * Block rules for where a ghost can be. Only reads chunks that are already loaded; unloaded space counts as
 * blocked. Doors, trapdoors, slabs etc. get no special handling: anything is judged purely by its collision shape.
 * Not thread-safe; one instance per search.
 */
final class GhostMovement {

    private final ServerLevel level;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    @Nullable
    private LevelChunk cachedChunk;
    private long cachedChunkKey = Long.MIN_VALUE;

    GhostMovement(ServerLevel level) {
        this.level = level;
    }

    @Nullable
    private BlockState state(int x, int y, int z) {
        if (y < this.level.getMinBuildHeight() || y >= this.level.getMaxBuildHeight()) {
            return null;
        }
        int chunkX = SectionPos.blockToSectionCoord(x);
        int chunkZ = SectionPos.blockToSectionCoord(z);
        long key = (long) chunkX << 32 | (chunkZ & 0xFFFFFFFFL);
        if (key != this.cachedChunkKey) {
            this.cachedChunk = this.level.getChunkSource().getChunkNow(chunkX, chunkZ);
            this.cachedChunkKey = key;
        }
        return this.cachedChunk == null ? null : this.cachedChunk.getBlockState(this.cursor.set(x, y, z));
    }

    /** A ghost's body can occupy this block: no collision, or something it can climb through. */
    boolean passable(int x, int y, int z) {
        BlockState state = state(x, y, z);
        if (state == null) {
            return false;
        }
        return state.is(BlockTags.CLIMBABLE) || state.getCollisionShape(this.level, this.cursor.set(x, y, z)).isEmpty();
    }

    /** Has something to stand on. */
    boolean solid(int x, int y, int z) {
        BlockState state = state(x, y, z);
        return state != null && !state.getCollisionShape(this.level, this.cursor.set(x, y, z)).isEmpty();
    }

    boolean fluid(int x, int y, int z) {
        BlockState state = state(x, y, z);
        return state != null && !state.getFluidState().isEmpty();
    }

    boolean climbable(int x, int y, int z) {
        BlockState state = state(x, y, z);
        return state != null && state.is(BlockTags.CLIMBABLE);
    }

    /** Feet here, head above: both clear. */
    boolean fits(int x, int y, int z) {
        return passable(x, y, z) && passable(x, y + 1, z);
    }

    /** A ghost can stay here: it fits, and is supported (ground or scaffolding-like below), swimming, or climbing. */
    boolean standable(int x, int y, int z) {
        return fits(x, y, z) && (solid(x, y - 1, z) || climbable(x, y - 1, z) || fluid(x, y, z) || climbable(x, y, z));
    }

    int minY() {
        return this.level.getMinBuildHeight();
    }
}
