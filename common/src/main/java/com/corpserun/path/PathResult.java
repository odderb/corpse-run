package com.corpserun.path;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * A ghost path, start to end, as feet positions.
 *
 * @param reachedTarget false for a best-effort partial path that ends at the point closest to the target
 */
public record PathResult(List<BlockPos> path, boolean reachedTarget) {
}
