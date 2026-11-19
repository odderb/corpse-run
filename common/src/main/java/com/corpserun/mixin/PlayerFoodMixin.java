package com.corpserun.mixin;

import com.corpserun.ghost.GhostManager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ghosts build up no exhaustion from moving, jumping, sprinting or swimming, so their hunger never drops. */
@Mixin(Player.class)
public abstract class PlayerFoodMixin {

    @Inject(method = "causeFoodExhaustion", at = @At("HEAD"), cancellable = true)
    private void corpserun$noGhostExhaustion(float exhaustion, CallbackInfo ci) {
        if (GhostManager.isGhost((Player) (Object) this)) {
            ci.cancel();
        }
    }
}
