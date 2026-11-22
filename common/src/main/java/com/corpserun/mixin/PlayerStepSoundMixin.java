package com.corpserun.mixin;

import com.corpserun.ghost.GhostManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ghosts walk silently: no footsteps (including the muffled and combined ones on carpet, snow layers... and splashes
 * while wading), on any block. Cancelled on both sides, since a player's own client plays its footsteps locally and
 * the server sends them to everyone else. Landing sounds are silenced in LivingEntityMixin, amethyst chimes in
 * EntityMixin.
 */
@Mixin(Player.class)
public abstract class PlayerStepSoundMixin {

    @Inject(method = "playStepSound", at = @At("HEAD"), cancellable = true)
    private void corpserun$ghostsWalkSilently(BlockPos pos, BlockState state, CallbackInfo ci) {
        if (GhostManager.isGhost((Entity) (Object) this)) {
            ci.cancel();
        }
    }
}
