package com.corpserun.job;

/** Returned by {@link JobScheduler#submit}; lets the submitter cancel the job or check whether it has ended. */
public final class JobHandle {

    private volatile boolean cancelRequested;
    private volatile boolean done;

    JobHandle() {
    }

    /** Requests cancellation. The callback receives CANCELLED on the next scheduler tick (if not already done). */
    public void cancel() {
        this.cancelRequested = true;
    }

    public boolean isCancelRequested() {
        return this.cancelRequested;
    }

    public boolean isDone() {
        return this.done;
    }

    void markDone() {
        this.done = true;
    }
}
