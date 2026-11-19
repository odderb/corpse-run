package com.corpserun;

import com.corpserun.job.JobScheduler;
import net.minecraft.server.MinecraftServer;

/**
 * Loader-agnostic handlers for the server lifecycle events each loader forwards here. Player death, respawn and
 * ticking are hooked directly in ServerPlayerMixin, so they behave identically on both loaders.
 */
public final class CorpseRunEvents {

    private CorpseRunEvents() {
    }

    /** End of every server tick. */
    public static void onServerTick(MinecraftServer server) {
        JobScheduler.tick(server);
    }

    public static void onServerStopping(MinecraftServer server) {
        JobScheduler.shutdown();
    }
}
