package com.corpserun.ghost;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/** The path walked during one visit to a dimension, oldest point first. Points are feet block positions. */
public final class TrailSegment {

    /** A jump bigger than this between consecutive points (pearl, /tp, elytra) starts a new run. */
    private static final double GAP_DISTANCE_SQR = 8.0 * 8.0;

    private final ResourceKey<Level> dimension;
    private final LongArrayList points;

    public TrailSegment(ResourceKey<Level> dimension) {
        this(dimension, new LongArrayList());
    }

    private TrailSegment(ResourceKey<Level> dimension, LongArrayList points) {
        this.dimension = dimension;
        this.points = points;
    }

    public ResourceKey<Level> dimension() {
        return this.dimension;
    }

    public int size() {
        return this.points.size();
    }

    public boolean isEmpty() {
        return this.points.isEmpty();
    }

    public BlockPos first() {
        return BlockPos.of(this.points.getLong(0));
    }

    public BlockPos last() {
        return BlockPos.of(this.points.getLong(this.points.size() - 1));
    }

    /** Squared distance from {@code pos} to the closest recorded point. */
    public double distSqrToClosest(BlockPos pos) {
        double best = Double.MAX_VALUE;
        for (int i = 0; i < this.points.size(); i++) {
            best = Math.min(best, BlockPos.of(this.points.getLong(i)).distSqr(pos));
        }
        return best;
    }

    void add(BlockPos pos) {
        this.points.add(pos.asLong());
    }

    void removeOldest() {
        this.points.removeLong(0);
    }

    /** The segment split at teleport-sized gaps; each run is continuous ground the player walked. */
    public List<List<BlockPos>> runs() {
        List<List<BlockPos>> runs = new ArrayList<>();
        List<BlockPos> current = new ArrayList<>();
        BlockPos previous = null;
        for (int i = 0; i < this.points.size(); i++) {
            BlockPos pos = BlockPos.of(this.points.getLong(i));
            if (previous != null && previous.distSqr(pos) > GAP_DISTANCE_SQR) {
                runs.add(current);
                current = new ArrayList<>();
            }
            current.add(pos);
            previous = pos;
        }
        if (!current.isEmpty()) {
            runs.add(current);
        }
        return runs;
    }

    TrailSegment copy() {
        return new TrailSegment(this.dimension, new LongArrayList(this.points));
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Dimension", this.dimension.location().toString());
        tag.putLongArray("Points", this.points.toLongArray());
        return tag;
    }

    static TrailSegment load(CompoundTag tag) {
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("Dimension")));
        return new TrailSegment(dimension, new LongArrayList(tag.getLongArray("Points")));
    }
}
