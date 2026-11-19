package com.corpserun.job;

/** How a job ended. Passed to its {@link JobCallback}. */
public enum JobStatus {
    /** The job finished its work; its result is available. */
    COMPLETED,
    /** The job threw an exception (logged by the scheduler). */
    FAILED,
    /** The job was cancelled through its handle, or the server stopped. */
    CANCELLED,
    /** The job ran past its timeout. */
    TIMED_OUT
}
