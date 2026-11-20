package com.corpserun.client;

import com.corpserun.entity.CorpseEntity;
import net.minecraft.client.Minecraft;

/** Tells corpses who the local player is, so a highlighted corpse only glows for its owner. */
public final class CorpseHighlight {

    private CorpseHighlight() {
    }

    /** Called at the end of every client tick (loader events). */
    public static void update() {
        Minecraft minecraft = Minecraft.getInstance();
        CorpseEntity.localPlayer = minecraft.player == null ? null : minecraft.player.getUUID();
    }
}
