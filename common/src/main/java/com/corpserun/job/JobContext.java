package com.corpserun.job;

import net.minecraft.server.MinecraftServer;

/** Passed to {@link Job#step}; tells the job when its slice of this tick's time budget is used up. */
public interface JobContext {

    /** True once the job should stop and return; it will be stepped again next tick. */
    boolean shouldYield();

    MinecraftServer server();
}
