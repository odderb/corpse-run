package com.corpserun.path;

import com.corpserun.job.Job;
import com.corpserun.job.JobContext;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/**
 * A* search for a ghost: walks in 8 directions, steps up one block, falls any distance (ghosts can't be hurt),
 * climbs ladders/vines/scaffolding and swims (water or lava) in any direction. Runs incrementally under the
 * {@link com.corpserun.job.JobScheduler}. If the target can't be reached within the node budget, the result is a
 * partial path to the explored point closest to the target.
 */
public final class GhostPathJob implements Job<PathResult> {

    private static final double DIAGONAL = Math.sqrt(2.0);
    private static final double STEP_UP_EXTRA = 0.5;
    private static final double CLIMB_COST = 1.5;
    private static final double FALL_PER_BLOCK = 0.1;
    private static final double FLUID_MULTIPLIER = 2.0;
    /** Ending within this distance of the target counts as reaching it (positions are block-precise). */
    private static final double REACHED_DISTANCE_SQR = 1.5 * 1.5;

    private static final int[][] HORIZONTAL = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    private final GhostMovement movement;
    private final BlockPos target;
    private final int maxNodes;

    private final PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));
    private final Long2ObjectOpenHashMap<Node> nodes = new Long2ObjectOpenHashMap<>();
    private Node best;
    private int expanded;
    @Nullable
    private PathResult result;

    public GhostPathJob(ServerLevel level, BlockPos start, BlockPos target, int maxNodes) {
        this.movement = new GhostMovement(level);
        this.target = target.immutable();
        this.maxNodes = maxNodes;

        Node startNode = new Node(start.getX(), start.getY(), start.getZ(), 0.0, heuristic(start.getX(), start.getY(), start.getZ()), null);
        this.nodes.put(BlockPos.asLong(start.getX(), start.getY(), start.getZ()), startNode);
        this.open.add(startNode);
        this.best = startNode;
    }

    @Override
    public boolean step(JobContext context) {
        while (!this.open.isEmpty()) {
            if (context.shouldYield()) {
                return false;
            }
            Node node = this.open.poll();
            if (node.closed) {
                continue; // stale queue entry superseded by a cheaper one
            }
            node.closed = true;

            if (node.h < this.best.h) {
                this.best = node;
            }
            if (distSqrToTarget(node) <= REACHED_DISTANCE_SQR) {
                this.result = new PathResult(build(node), true);
                return true;
            }
            if (++this.expanded >= this.maxNodes) {
                break;
            }
            expand(node);
        }
        this.result = new PathResult(build(this.best), false);
        return true;
    }

    @Override
    @Nullable
    public PathResult result() {
        return this.result;
    }

    private void expand(Node node) {
        int x = node.x;
        int y = node.y;
        int z = node.z;
        GhostMovement m = this.movement;
        boolean inFluid = m.fluid(x, y, z);

        for (int[] dir : HORIZONTAL) {
            int dx = dir[0];
            int dz = dir[1];
            boolean diagonal = dx != 0 && dz != 0;
            // No cutting corners: both orthogonal neighbours must be clear for a diagonal move.
            if (diagonal && !(m.fits(x + dx, y, z) && m.fits(x, y, z + dz))) {
                continue;
            }
            int nx = x + dx;
            int nz = z + dz;
            double base = diagonal ? DIAGONAL : 1.0;

            if (m.standable(nx, y, nz)) {
                visit(node, nx, y, nz, base);
            } else if (m.fits(nx, y, nz)) {
                // Open air ahead: fall until something holds us. Ghosts take no fall damage.
                int fy = y - 1;
                while (fy >= m.minY() && m.fits(nx, fy, nz) && !m.standable(nx, fy, nz)) {
                    fy--;
                }
                if (fy >= m.minY() && m.standable(nx, fy, nz)) {
                    visit(node, nx, fy, nz, base + (y - fy) * FALL_PER_BLOCK);
                }
            } else if (!diagonal && m.passable(x, y + 2, z) && m.standable(nx, y + 1, nz)) {
                // Step up one block (needs head room to jump).
                visit(node, nx, y + 1, nz, base + STEP_UP_EXTRA);
            }
        }

        // Vertical movement while climbing or swimming.
        if (inFluid || m.climbable(x, y, z)) {
            if (m.standable(x, y + 1, z) || (m.fits(x, y + 1, z) && (m.fluid(x, y + 1, z) || m.climbable(x, y + 1, z)))) {
                visit(node, x, y + 1, z, CLIMB_COST);
            }
            if (m.fits(x, y - 1, z)) {
                visit(node, x, y - 1, z, CLIMB_COST);
            }
        }
    }

    private void visit(Node from, int x, int y, int z, double moveCost) {
        if (this.movement.fluid(x, y, z)) {
            moveCost *= FLUID_MULTIPLIER;
        }
        double g = from.g + moveCost;
        long key = BlockPos.asLong(x, y, z);
        Node existing = this.nodes.get(key);
        if (existing != null) {
            if (existing.closed || existing.g <= g) {
                return;
            }
            existing.closed = true; // retire the old queue entry; a fresh one replaces it
        }
        Node next = new Node(x, y, z, g, heuristic(x, y, z), from);
        this.nodes.put(key, next);
        this.open.add(next);
    }

    private double heuristic(int x, int y, int z) {
        double dx = x - this.target.getX();
        double dy = y - this.target.getY();
        double dz = z - this.target.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private double distSqrToTarget(Node node) {
        return node.h * node.h;
    }

    private static List<BlockPos> build(Node end) {
        List<BlockPos> path = new ArrayList<>();
        for (Node n = end; n != null; n = n.parent) {
            path.add(new BlockPos(n.x, n.y, n.z));
        }
        Collections.reverse(path);
        return path;
    }

    private static final class Node {

        final int x;
        final int y;
        final int z;
        final double g;
        final double h;
        final double f;
        @Nullable
        final Node parent;
        boolean closed;

        Node(int x, int y, int z, double g, double h, @Nullable Node parent) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.g = g;
            this.h = h;
            this.f = g + h;
            this.parent = parent;
        }
    }
}
