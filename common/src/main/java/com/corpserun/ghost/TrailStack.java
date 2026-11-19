package com.corpserun.ghost;

import com.corpserun.config.CorpseRunConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * Everywhere a player walked this life, as a stack of per-dimension segments: entering a dimension pushes a new
 * segment (revisits never resume an old one). Frozen into the corpse on death; a ghost follows the top-most
 * segment for whatever dimension it's in. Capped at {@link CorpseRunConfig#trailMaxPoints()} total points, oldest
 * dropped first.
 */
public final class TrailStack {

    /** Distance between recorded points. Positions are block-precise, so this works out to ~2 blocks. */
    private static final double SPACING_SQR = 1.5 * 1.5;

    /** Bottom (oldest) first, top (current) last. */
    private final Deque<TrailSegment> segments = new ArrayDeque<>();
    private int totalPoints;

    public void record(ServerPlayer player) {
        ResourceKey<Level> dimension = player.level().dimension();
        TrailSegment top = this.segments.peekLast();
        if (top == null || !top.dimension().equals(dimension)) {
            top = new TrailSegment(dimension);
            this.segments.addLast(top);
        }

        if (player.isSpectator() || player.isPassenger()) {
            return;
        }
        // Mid-air points (jumps, elytra) are useless to a walking ghost.
        if (!player.onGround() && !player.isInLiquid() && !player.onClimbable()) {
            return;
        }

        BlockPos pos = player.blockPosition();
        if (!top.isEmpty() && top.last().distSqr(pos) < SPACING_SQR) {
            return;
        }
        top.add(pos);
        this.totalPoints++;
        trim();
    }

    /** Appends a point to the top segment if it's in that dimension (used to end the trail at the corpse). */
    public void append(ResourceKey<Level> dimension, BlockPos pos) {
        TrailSegment top = this.segments.peekLast();
        if (top == null || !top.dimension().equals(dimension)) {
            top = new TrailSegment(dimension);
            this.segments.addLast(top);
        }
        top.add(pos);
        this.totalPoints++;
        trim();
    }

    private void trim() {
        int max = CorpseRunConfig.trailMaxPoints();
        while (this.totalPoints > max && !this.segments.isEmpty()) {
            TrailSegment bottom = this.segments.peekFirst();
            if (bottom.isEmpty()) {
                this.segments.pollFirst();
                continue;
            }
            bottom.removeOldest();
            this.totalPoints--;
            if (bottom.isEmpty() && this.segments.size() > 1) {
                this.segments.pollFirst();
            }
        }
    }

    /** The most recent non-empty segment for a dimension, or null if this life never walked there. */
    @Nullable
    public TrailSegment topFor(ResourceKey<Level> dimension) {
        Iterator<TrailSegment> it = this.segments.descendingIterator();
        while (it.hasNext()) {
            TrailSegment segment = it.next();
            if (segment.dimension().equals(dimension) && !segment.isEmpty()) {
                return segment;
            }
        }
        return null;
    }

    public int segmentCount() {
        return this.segments.size();
    }

    public int totalPoints() {
        return this.totalPoints;
    }

    public void clear() {
        this.segments.clear();
        this.totalPoints = 0;
    }

    public TrailStack copy() {
        TrailStack copy = new TrailStack();
        for (TrailSegment segment : this.segments) {
            copy.segments.addLast(segment.copy());
        }
        copy.totalPoints = this.totalPoints;
        return copy;
    }

    public void copyFrom(TrailStack other) {
        clear();
        for (TrailSegment segment : other.segments) {
            this.segments.addLast(segment.copy());
        }
        this.totalPoints = other.totalPoints;
    }

    public ListTag save() {
        ListTag list = new ListTag();
        for (TrailSegment segment : this.segments) {
            list.add(segment.save());
        }
        return list;
    }

    public static TrailStack load(ListTag list) {
        TrailStack stack = new TrailStack();
        for (int i = 0; i < list.size(); i++) {
            TrailSegment segment = TrailSegment.load(list.getCompound(i));
            stack.segments.addLast(segment);
            stack.totalPoints += segment.size();
        }
        return stack;
    }

    public static TrailStack load(CompoundTag parent, String key) {
        return load(parent.getList(key, Tag.TAG_COMPOUND));
    }
}
