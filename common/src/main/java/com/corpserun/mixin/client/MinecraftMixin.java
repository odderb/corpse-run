package com.corpserun.mixin.client;

import com.corpserun.ghost.GhostCall;
import com.corpserun.ghost.GhostCallPayload;
import com.corpserun.ghost.GhostManager;
import com.corpserun.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A ghost's left click doesn't attack, swing or start mining: it asks the server to play the ghost call sound.
 * The client also keeps the cooldown so held or spammed clicks don't flood the server with packets.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Shadow
    @Nullable
    public LocalPlayer player;

    @Unique
    private int corpserun$nextCallTick;

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void corpserun$ghostCall(CallbackInfoReturnable<Boolean> cir) {
        if (this.player == null || !GhostManager.isGhost(this.player)) {
            return;
        }
        cir.setReturnValue(false);
        // The cooldown is in the local player's ticks, but a new LocalPlayer (respawn, dimension change, joining a
        // world) starts again from 0: a cooldown further ahead than one throttle period is left over from an old one.
        int wait = this.corpserun$nextCallTick - this.player.tickCount;
        if (wait <= 0 || wait > GhostCall.CLIENT_THROTTLE_TICKS) {
            this.corpserun$nextCallTick = this.player.tickCount + GhostCall.CLIENT_THROTTLE_TICKS;
            Services.PLATFORM.sendToServer(GhostCallPayload.INSTANCE);
        }
    }

    /** Holding the button would otherwise keep trying to mine (and show cracks/particles) where ghosts can't. */
    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void corpserun$ghostNoMining(boolean leftClick, CallbackInfo ci) {
        if (this.player != null && GhostManager.isGhost(this.player)) {
            ci.cancel();
        }
    }
}
