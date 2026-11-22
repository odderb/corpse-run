package com.corpserun.mixin;

import com.corpserun.config.CorpseRunConfig;
import com.corpserun.ghost.GhostCall;
import com.corpserun.ghost.GhostManager;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * With sculkIgnoresGhosts on, ghosts cause no game events (steps, landing, swimming, opening doors...), so sculk sensors, shriekers, wardens and
 * anything else listening for vibrations never notice them. The one exception is a howl (GhostCall#handle, when
 * howlTriggersSculk is on), which is meant to set sculk off. Every game event in a server level passes through here.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    @Inject(method = "gameEvent(Lnet/minecraft/core/Holder;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/gameevent/GameEvent$Context;)V",
            at = @At("HEAD"), cancellable = true)
    private void corpserun$ghostsMakeNoVibrations(Holder<GameEvent> gameEvent, Vec3 pos, GameEvent.Context context, CallbackInfo ci) {
        if (CorpseRunConfig.sculkIgnoresGhosts() && GhostManager.isGhost(context.sourceEntity()) && !GhostCall.isEmittingHowl()) {
            ci.cancel();
        }
    }
}
