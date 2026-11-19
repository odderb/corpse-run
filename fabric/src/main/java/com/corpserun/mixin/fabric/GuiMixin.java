package com.corpserun.mixin.fabric;

import com.corpserun.client.GhostHud;
import com.corpserun.client.GhostScreenEffect;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric API has no pre-HUD event or way to skip individual vanilla HUD elements, so these hook Gui directly.
 * (NeoForge uses RenderGuiEvent.Pre and RenderGuiLayerEvent.Pre for the same things.)
 */
@Mixin(Gui.class)
public abstract class GuiMixin {

    /** Before anything of the HUD is drawn: the ghost static goes over the finished world frame. */
    @Inject(method = "render", at = @At("HEAD"))
    private void corpserun$ghostScreenEffect(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        GhostScreenEffect.render(guiGraphics, deltaTracker);
    }

    /** renderPlayerHealth draws hearts, armor, hunger and air bubbles. */
    @Inject(method = {"renderItemHotbar", "renderSelectedItemName", "renderPlayerHealth", "renderExperienceBar",
            "renderExperienceLevel"},
            at = @At("HEAD"), cancellable = true)
    private void corpserun$hideGhostHud(CallbackInfo ci) {
        if (GhostHud.hideGhostHud()) {
            ci.cancel();
        }
    }
}
