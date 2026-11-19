package com.corpserun.mixin.client;

import com.corpserun.client.GhostScreenEffect;
import com.corpserun.ghost.GhostManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    /**
     * No view bobbing (the camera and hand bob/sway while walking) as a ghost, as if the "View Bobbing" option were
     * off. Covers both the world and the first-person hand, which share this method.
     */
    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void corpserun$noGhostViewBob(PoseStack poseStack, float partialTicks, CallbackInfo ci) {
        if (GhostManager.isGhost(Minecraft.getInstance().getCameraEntity())) {
            ci.cancel();
        }
    }

    /** Shaders were reloaded (resource reload): rebuild the static's post chain from the new files. */
    @Inject(method = "reloadShaders", at = @At("TAIL"))
    private void corpserun$reloadGhostScreenEffect(ResourceProvider resourceProvider, CallbackInfo ci) {
        GhostScreenEffect.reset();
    }
}
