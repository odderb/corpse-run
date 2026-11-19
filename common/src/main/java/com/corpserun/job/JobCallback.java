package com.corpserun.job;

import org.jetbrains.annotations.Nullable;

/**
 * Called exactly once, on the server thread, when a job ends. {@code handle} is the one {@link JobScheduler#submit}
 * returned for it; {@code result} is only non-null for COMPLETED.
 */
@FunctionalInterface
public interface JobCallback<T> {

    void onFinished(JobHandle handle, JobStatus status, @Nullable T result);
}
