package com.corpserun.client;

import com.corpserun.Constants;
import com.corpserun.config.CorpseRunClientConfig;
import com.corpserun.ghost.GhostManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Full-screen static shown while the local player is a ghost. Owns its own {@link PostChain} rather than using
 * GameRenderer's single post-effect slot, which vanilla reclaims for spectator mob views.
 * <p>
 * Drawn at the very start of GUI rendering (NeoForge: RenderGuiEvent.Pre, Fabric: the head of Gui.render), the first
 * point where the finished frame (world, hand, and any shader pack composite from Iris) is in the main render target
 * and nothing of the HUD is drawn yet. It hooks no world-rendering code, so renderer replacements like Sodium and
 * Iris don't affect it. It shows even with the HUD hidden (F1).
 */
public final class GhostScreenEffect {

    private static final ResourceLocation CHAIN = Constants.id("shaders/post/ghost_static.json");

    @Nullable
    private static PostChain chain;
    private static int width = -1;
    private static int height = -1;
    /** Set after a load failure so a broken shader doesn't retry (and log) every frame. Cleared on reload. */
    private static boolean failed;

    private GhostScreenEffect() {
    }

    /** Called before the HUD is drawn. */
    public static void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!CorpseRunClientConfig.ghostScreenEffect() || minecraft.player == null || !GhostManager.isGhost(minecraft.player)) {
            close();
            return;
        }

        PostChain postChain = getOrCreate(minecraft);
        if (postChain == null) {
            return;
        }

        // Anything already queued for the GUI belongs under the static, not on top of it.
        guiGraphics.flush();
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.resetTextureMatrix();
        postChain.process(deltaTracker.getGameTimeDeltaTicks());
        // Back to the main target with the full-window viewport, and the depth test the HUD renders with.
        minecraft.getMainRenderTarget().bindWrite(true);
        RenderSystem.enableDepthTest();
    }

    @Nullable
    private static PostChain getOrCreate(Minecraft minecraft) {
        Window window = minecraft.getWindow();
        if (chain == null && !failed) {
            try {
                chain = new PostChain(minecraft.getTextureManager(), minecraft.getResourceManager(), minecraft.getMainRenderTarget(), CHAIN);
                width = -1;
            } catch (Exception e) {
                Constants.LOG.error("Failed to load ghost screen effect {}", CHAIN, e);
                failed = true;
                return null;
            }
        }
        if (chain != null && (window.getWidth() != width || window.getHeight() != height)) {
            width = window.getWidth();
            height = window.getHeight();
            chain.resize(width, height);
        }
        return chain;
    }

    /** Resource reload: drop the chain so it's rebuilt from the (possibly changed) shader files. */
    public static void reset() {
        close();
        failed = false;
    }

    private static void close() {
        if (chain != null) {
            chain.close();
            chain = null;
        }
    }
}
