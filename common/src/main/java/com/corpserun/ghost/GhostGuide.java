package com.corpserun.ghost;

import com.corpserun.CorpseRun;
import com.corpserun.config.CorpseRunConfig;
import com.corpserun.job.JobHandle;
import com.corpserun.job.JobScheduler;
import com.corpserun.job.JobStatus;
import com.corpserun.path.GhostPathJob;
import com.corpserun.path.PathResult;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-ghost footprint guidance (not saved). In the ghost's current dimension it takes the top-most trail
 * segment, pathfinds from the ghost to a point on it near the ghost, and shows footprints along
 * [path] + [rest of that run], which ends at the corpse or at the portal the player left through. At the end of a
 * run (a teleport/elytra gap follows) it pathfinds on to the start of the next run.
 * <p>
 * The point joined is the one furthest along the trail among those about as near as the nearest (see
 * {@link #TRAIL_JOIN_SLACK}), not simply the nearest: the nearest is often just behind the ghost, and the trail
 * replays everywhere the last life went, so it can pass the same spot several times. Joining the latest pass skips
 * the loops in between instead of leading the ghost back along them.
 */
public final class GhostGuide {

    /** Ticks between two footprints of a sweep. */
    private static final int STEP_INTERVAL = 2;
    /** Ticks between the end of one sweep and the start of the next. */
    private static final int SWEEP_PAUSE = 12;
    private static final int REPATH_CHECK_INTERVAL = 40;
    /** Further than this from the current route means the ghost wandered off and needs a new path. */
    private static final double OFF_ROUTE_DISTANCE_SQR = 3.0 * 3.0;
    /** Within this of a run's last point counts as having finished it. */
    private static final double RUN_END_DISTANCE_SQR = 3.0 * 3.0;
    /**
     * Trail points up to this much further away than the nearest one still count as near; the latest of those is
     * joined. Wide enough to skip a point just behind the ghost or a trail loop passing close by, small enough not
     * to aim for a pass of the trail that is really somewhere else.
     */
    private static final double TRAIL_JOIN_SLACK = 4.0;
    /** The same for where a sweep of footprints starts on the route: it starts at the latest nearby route point. */
    private static final double SWEEP_START_SLACK = 2.0;
    /** Closer than this to the target needs no pathfinding. */
    private static final double ON_TRAIL_DISTANCE_SQR = 2.0 * 2.0;
    /** Within this of the corpse, footprints stop: the ghost is standing on it. */
    private static final double AT_CORPSE_DISTANCE_SQR = 1.0 * 1.0;
    /** Distance between footprints, measured along the route (they're placed evenly, between route points too). */
    private static final double PRINT_SPACING = 1.25;
    private static final int PATH_MAX_NODES = 30_000;
    private static final int PATH_TIMEOUT_TICKS = 100;

    /** Heel-to-toe distance; the bigger toe dot points the way. */
    private static final double FOOT_LENGTH = 0.5;
    /** Footprints appear this far above the ground and sink down onto it as they fade (see FootprintParticle). */
    private static final double SPAWN_HEIGHT = 1.0;
    /** Sideways drift toward the next footprint, in blocks per tick, so clusters still show which way to go. */
    private static final double DRIFT_SPEED = 0.025;

    /** Footprint route, start to end. */
    private List<BlockPos> route = List.of();
    @Nullable
    private TrailSegment routeSegment;
    @Nullable
    private JobHandle pending;
    /** The sweep being shown: footprints placed one at a time, from the ghost outward, so they read as walking away. */
    private List<Footprint> sweep = List.of();
    private int sweepIndex;
    private int nextSweepTick;
    /** The dimension the route and any search in flight belong to. */
    @Nullable
    private ResourceKey<Level> dimension;

    private record Footprint(Vec3 heel, Vec3 toe, Vec3 drift) {}

    /**
     * @param showFootprints   new sweeps may start: always, or only while howling (footprintsOnlyWhenHowling). A
     *                         sweep already on its way finishes after this turns false; no new one starts.
     */
    public void tick(ServerPlayer player, CorpseLocation corpse, boolean showFootprints) {
        if (!CorpseRunConfig.footprints()) {
            reset();
            return;
        }
        ResourceKey<Level> current = player.level().dimension();
        boolean changedDimension = this.dimension != null && !this.dimension.equals(current);
        if (changedDimension) {
            // Portal, /tp, /corpse reset...: the old route and any search running in the old level are useless.
            reset();
        }
        this.dimension = current;
        // Standing on the corpse: there's nowhere left to lead. Anywhere further out still gets footprints, even
        // within resurrect range.
        if (current.equals(corpse.dimension()) && player.position().distanceToSqr(corpse.pos()) <= AT_CORPSE_DISTANCE_SQR) {
            this.sweep = List.of();
            this.sweepIndex = 0;
            return;
        }
        if (changedDimension || player.tickCount % REPATH_CHECK_INTERVAL == 0) {
            maybeRepath(player, corpse.trail());
        }
        // The route keeps being planned in the background, so footprints appear right away on a howl.
        if (player.tickCount % STEP_INTERVAL == 0) {
            stepSweep(player, showFootprints);
        }
    }

    /** Drop the route and cancel any search in flight (resurrect, respawn, feature disabled). */
    public void reset() {
        if (this.pending != null) {
            this.pending.cancel();
            this.pending = null;
        }
        this.route = List.of();
        this.routeSegment = null;
        this.sweep = List.of();
        this.sweepIndex = 0;
    }

    private void maybeRepath(ServerPlayer player, TrailStack trail) {
        TrailSegment segment = trail.topFor(player.level().dimension());
        if (segment == null) {
            reset();
            return;
        }
        boolean needed = segment != this.routeSegment
                || this.route.isEmpty()
                || distSqrToRoute(player.blockPosition()) > OFF_ROUTE_DISTANCE_SQR
                || nearEndWithMoreToGo(player.blockPosition(), segment);
        if (needed && (this.pending == null || this.pending.isDone())) {
            repath(player, segment);
        }
    }

    /** At the end of the route while the segment continues past a gap: time to bridge to the next run. */
    private boolean nearEndWithMoreToGo(BlockPos feet, TrailSegment segment) {
        BlockPos end = this.route.get(this.route.size() - 1);
        BlockPos segmentEnd = segment.last();
        return !end.equals(segmentEnd) && end.distSqr(feet) <= RUN_END_DISTANCE_SQR;
    }

    private void repath(ServerPlayer player, TrailSegment segment) {
        BlockPos feet = player.blockPosition();
        List<List<BlockPos>> runs = segment.runs();

        // Nearest trail point to the ghost...
        double nearest = Double.MAX_VALUE;
        for (List<BlockPos> run : runs) {
            for (BlockPos pos : run) {
                nearest = Math.min(nearest, pos.distSqr(feet));
            }
        }
        // ...then the latest point (runs in order, points in order) about as near, so the ghost is never led back.
        double reach = Math.sqrt(nearest) + TRAIL_JOIN_SLACK;
        double reachSqr = reach * reach;
        int runIndex = 0;
        int pointIndex = 0;
        double bestDist = nearest;
        for (int r = 0; r < runs.size(); r++) {
            List<BlockPos> run = runs.get(r);
            for (int p = 0; p < run.size(); p++) {
                double d = run.get(p).distSqr(feet);
                if (d <= reachSqr) {
                    runIndex = r;
                    pointIndex = p;
                    bestDist = d;
                }
            }
        }
        // Finished this run and another follows: head for the start of the next one.
        List<BlockPos> run = runs.get(runIndex);
        if (pointIndex == run.size() - 1 && bestDist <= RUN_END_DISTANCE_SQR && runIndex + 1 < runs.size()) {
            runIndex++;
            pointIndex = 0;
            run = runs.get(runIndex);
        }

        List<BlockPos> remainder = List.copyOf(run.subList(pointIndex, run.size()));
        BlockPos target = remainder.get(0);
        this.routeSegment = segment;

        if (target.distSqr(feet) <= ON_TRAIL_DISTANCE_SQR) {
            this.route = remainder;
            return;
        }
        if (this.route.isEmpty()) {
            // Something to show while the search runs.
            this.route = remainder;
        }

        GhostPathJob job = new GhostPathJob(player.serverLevel(), feet, target, PATH_MAX_NODES);
        this.pending = JobScheduler.submit(job, PATH_TIMEOUT_TICKS,
                (handle, status, result) -> onPathFinished(handle, segment, remainder, status, result));
    }

    private void onPathFinished(JobHandle handle, TrailSegment segment, List<BlockPos> remainder, JobStatus status, @Nullable PathResult result) {
        if (handle != this.pending || segment != this.routeSegment) {
            return; // superseded
        }
        this.pending = null;
        if (status != JobStatus.COMPLETED || result == null) {
            this.route = remainder;
            return;
        }
        List<BlockPos> combined = new ArrayList<>(result.path());
        if (result.reachedTarget()) {
            combined.addAll(remainder);
        }
        // A partial path stops short; it still leads the right way and is retried as the ghost moves.
        this.route = combined;
    }

    private double distSqrToRoute(BlockPos feet) {
        double best = Double.MAX_VALUE;
        for (BlockPos pos : this.route) {
            best = Math.min(best, pos.distSqr(feet));
        }
        return best;
    }

    /** Shows the next footprint of the sweep, or starts a new sweep from the ghost's current position. */
    private void stepSweep(ServerPlayer player, boolean startNew) {
        if (this.sweepIndex >= this.sweep.size()) {
            if (!startNew || player.tickCount < this.nextSweepTick) {
                return;
            }
            this.sweep = planSweep(player);
            this.sweepIndex = 0;
            if (this.sweep.isEmpty()) {
                return;
            }
        }
        Footprint print = this.sweep.get(this.sweepIndex++);
        ServerLevel level = player.serverLevel();
        // Count 0 sends the offset as the particle's velocity: the drift toward the next footprint.
        Vec3 drift = print.drift();
        level.sendParticles(player, CorpseRun.FOOTPRINT_HEEL.get(), true, print.heel().x, print.heel().y, print.heel().z, 0, drift.x, 0.0, drift.z, 1.0);
        level.sendParticles(player, CorpseRun.FOOTPRINT_TOE.get(), true, print.toe().x, print.toe().y, print.toe().z, 0, drift.x, 0.0, drift.z, 1.0);
        if (this.sweepIndex >= this.sweep.size()) {
            this.nextSweepTick = player.tickCount + SWEEP_PAUSE;
        }
    }

    /** Footprints from the ghost's position along the next {@code guidanceLookahead} blocks of the route. */
    private List<Footprint> planSweep(ServerPlayer player) {
        if (this.route.isEmpty()) {
            return List.of();
        }
        // From the latest route point about as near as the nearest, so a route that doubles back past the ghost
        // doesn't send the footprints around the loop (or a step behind it first).
        BlockPos feet = player.blockPosition();
        double nearest = Double.MAX_VALUE;
        for (BlockPos pos : this.route) {
            nearest = Math.min(nearest, pos.distSqr(feet));
        }
        double reach = Math.sqrt(nearest) + SWEEP_START_SLACK;
        int start = 0;
        for (int i = 0; i < this.route.size(); i++) {
            if (this.route.get(i).distSqr(feet) <= reach * reach) {
                start = i;
            }
        }

        // Walk the route as a line from the closest point, dropping a footprint every PRINT_SPACING blocks along it,
        // wherever that falls between route points, so they're evenly spaced and follow the path's actual shape.
        List<Footprint> prints = new ArrayList<>();
        double lookahead = CorpseRunConfig.guidanceLookahead();
        double travelled = 0.0;
        double nextPrintAt = 0.0;
        Vec3 flat = Vec3.ZERO;
        for (int i = start; i + 1 < this.route.size() && travelled <= lookahead; i++) {
            Vec3 a = Vec3.atBottomCenterOf(this.route.get(i));
            Vec3 b = Vec3.atBottomCenterOf(this.route.get(i + 1));
            Vec3 segment = b.subtract(a);
            double length = segment.length();
            if (length < 1.0E-6) {
                continue;
            }
            Vec3 dir = segment.scale(1.0 / length);
            Vec3 segmentFlat = new Vec3(dir.x, 0.0, dir.z);
            if (segmentFlat.lengthSqr() > 1.0E-6) {
                flat = segmentFlat.normalize();   // straight up/down segments keep the last heading
            }
            while (nextPrintAt <= travelled + length && nextPrintAt <= lookahead) {
                prints.add(footprint(a.add(dir.scale(nextPrintAt - travelled)), flat, prints.size()));
                nextPrintAt += PRINT_SPACING;
            }
            travelled += length;
        }
        if (prints.isEmpty()) {
            // Standing on the route's last point: one footprint right there.
            prints.add(footprint(Vec3.atBottomCenterOf(this.route.get(this.route.size() - 1)), flat, 0));
        }
        return prints;
    }

    private static Footprint footprint(Vec3 point, Vec3 flat, int index) {
        // Alternate left/right of the walking direction so it reads as steps.
        Vec3 side = new Vec3(-flat.z, 0.0, flat.x).scale(index % 2 == 0 ? 0.25 : -0.25);
        Vec3 center = point.add(side).add(0.0, 0.05 + SPAWN_HEIGHT, 0.0);
        Vec3 half = flat.scale(FOOT_LENGTH / 2.0);
        return new Footprint(center.subtract(half), center.add(half), flat.scale(DRIFT_SPEED));
    }
}
