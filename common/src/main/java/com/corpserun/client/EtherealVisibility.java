package com.corpserun.client;

import com.corpserun.block.EtherealBlock;
import com.corpserun.ghost.GhostManager;
import net.minecraft.client.Minecraft;

/** Draws ethereal blocks only while the local player is a ghost, rebuilding chunk meshes when that changes. */
public final class EtherealVisibility {

    private EtherealVisibility() {
    }

    /** Called at the end of every client tick (loader events). */
    public static void update() {
        Minecraft minecraft = Minecraft.getInstance();
        boolean ghost = minecraft.player != null && GhostManager.isGhost(minecraft.player);
        if (ghost != EtherealBlock.visible) {
            EtherealBlock.visible = ghost;
            if (minecraft.level != null) {
                minecraft.levelRenderer.allChanged();
            }
        }
    }
}
