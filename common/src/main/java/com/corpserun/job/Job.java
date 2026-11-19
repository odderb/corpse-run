package com.corpserun.job;

import org.jetbrains.annotations.Nullable;

/**
 * Incremental unit of work run on the server thread by {@link JobScheduler}. Each tick the scheduler calls
 * {@link #step} with a time-limited context; the job works until {@link JobContext#shouldYield()} and returns.
 */
public interface Job<T> {

    /**
     * Do some work.
     *
     * @return true when the job is finished and {@link #result()} is ready, false to be stepped again next tick
     * @throws Exception any exception ends the job with {@link JobStatus#FAILED}
     */
    boolean step(JobContext context) throws Exception;

    @Nullable
    T result();
}
