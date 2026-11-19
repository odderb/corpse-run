package com.corpserun.mixin;

import com.corpserun.ghost.GhostManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ghosts can't pick this up. */
@Mixin(AbstractArrow.class)
public abstract class AbstractArrowMixin {

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void corpserun$ghostCannotPickUp(Player player, CallbackInfo ci) {
        if (GhostManager.isGhost(player)) {
            ci.cancel();
        }
    }
}
