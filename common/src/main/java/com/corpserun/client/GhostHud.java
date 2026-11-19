package com.corpserun.client;

import com.corpserun.config.CorpseRunClientConfig;
import com.corpserun.ghost.GhostManager;
import net.minecraft.client.Minecraft;

/**
 * While the local player is a ghost (and {@code ghostHideHud} is on), the hotbar with its selected item name, the
 * health, armor and hunger bars, air bubbles and the XP bar and level aren't drawn. Only drawing is affected: the inventory still opens and works.
 * Hooked in by the loaders (NeoForge: GUI layer event, Fabric: GuiMixin).
 */
public final class GhostHud {

    private GhostHud() {
    }

    public static boolean hideGhostHud() {
        Minecraft minecraft = Minecraft.getInstance();
        return CorpseRunClientConfig.ghostHideHud() && minecraft.player != null && GhostManager.isGhost(minecraft.player);
    }
}
