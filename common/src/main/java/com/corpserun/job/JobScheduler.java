package com.corpserun.job;

import com.corpserun.Constants;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Runs {@link Job}s on the server thread, spread across ticks. Each tick the queued jobs share a fixed time
 * budget, round-robin, so long work never causes a lag spike. Loaders drive it from their server tick and
 * server stopping events via {@link #tick} and {@link #shutdown}.
 */
public final class JobScheduler {

    /** Total time all jobs may use per server tick. */
    private static final long TICK_BUDGET_NANOS = 2_000_000L;

    private static final Deque<Entry<?>> QUEUE = new ArrayDeque<>();

    private JobScheduler() {
    }

    /**
     * Queue a job.
     *
     * @param timeoutTicks the job ends with TIMED_OUT if still running this many ticks after submission
     */
    public static <T> JobHandle submit(Job<T> job, int timeoutTicks, JobCallback<T> callback) {
        JobHandle handle = new JobHandle();
        QUEUE.addLast(new Entry<>(job, callback, handle, timeoutTicks));
        return handle;
    }

    public static void tick(MinecraftServer server) {
        if (QUEUE.isEmpty()) {
            return;
        }

        long tickDeadline = System.nanoTime() + TICK_BUDGET_NANOS;
        int count = QUEUE.size();
        for (int i = 0; i < count && !QUEUE.isEmpty(); i++) {
            Entry<?> entry = QUEUE.pollFirst();
            entry.ageTicks++;

            if (entry.handle.isCancelRequested()) {
                entry.finish(JobStatus.CANCELLED);
                continue;
            }
            if (entry.ageTicks > entry.timeoutTicks) {
                entry.finish(JobStatus.TIMED_OUT);
                continue;
            }

            long now = System.nanoTime();
            if (now >= tickDeadline) {
                // Out of budget: this job and the rest wait for next tick, in order.
                QUEUE.addFirst(entry);
                break;
            }
            // Fair share of what's left of the budget among the jobs not yet stepped this tick.
            long sliceDeadline = now + (tickDeadline - now) / (count - i);
            Context context = new Context(server, sliceDeadline);

            boolean finished;
            try {
                finished = entry.job.step(context);
            } catch (Exception e) {
                Constants.LOG.error("Job {} failed", entry.job, e);
                entry.finish(JobStatus.FAILED);
                continue;
            }

            if (finished) {
                entry.finish(JobStatus.COMPLETED);
            } else {
                QUEUE.addLast(entry);
            }
        }
    }

    /** Server stopping: every pending job ends with CANCELLED. */
    public static void shutdown() {
        while (!QUEUE.isEmpty()) {
            QUEUE.pollFirst().finish(JobStatus.CANCELLED);
        }
    }

    private record Context(MinecraftServer server, long deadline) implements JobContext {

        @Override
        public boolean shouldYield() {
            return System.nanoTime() >= this.deadline;
        }
    }

    private static final class Entry<T> {

        final Job<T> job;
        final JobCallback<T> callback;
        final JobHandle handle;
        final int timeoutTicks;
        int ageTicks;

        Entry(Job<T> job, JobCallback<T> callback, JobHandle handle, int timeoutTicks) {
            this.job = job;
            this.callback = callback;
            this.handle = handle;
            this.timeoutTicks = timeoutTicks;
        }

        void finish(JobStatus status) {
            this.handle.markDone();
            @Nullable T result = status == JobStatus.COMPLETED ? this.job.result() : null;
            try {
                this.callback.onFinished(this.handle, status, result);
            } catch (Exception e) {
                Constants.LOG.error("Callback for job {} threw", this.job, e);
            }
        }
    }
}
