package com.corpserun.mixin;

import com.corpserun.CorpseRun;
import com.corpserun.block.EtherealBlocks;
import com.corpserun.ghost.GhostManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A ghost's footsteps on (or climbing inside) an ethereal block are only heard by players who can see ghosts,
 * like the block itself. Footsteps on anything else are heard by everyone as usual. Server side only: the walking
 * ghost's own client plays its own footsteps locally, as vanilla does.
 */
@Mixin(Player.class)
public abstract class PlayerStepSoundMixin {

    @Inject(method = "playStepSound", at = @At("HEAD"), cancellable = true)
    private void corpserun$etherealStepsGhostOnly(BlockPos pos, BlockState state, CallbackInfo ci) {
        if (!((Object) this instanceof ServerPlayer ghost) || !GhostManager.isGhost(ghost)) {
            return;
        }
        boolean ethereal = state.is(CorpseRun.ETHEREAL_BLOCK.get()) || ghost.getInBlockState().is(CorpseRun.ETHEREAL_BLOCK.get());
        if (ethereal) {
            ci.cancel();
            if (!ghost.isSilent()) {
                SoundType sound = CorpseRun.ETHEREAL_BLOCK.get().defaultBlockState().getSoundType();
                EtherealBlocks.playForGhostViewers(ghost, sound.getStepSound(), sound.getVolume() * 0.15F, sound.getPitch(),
                        ghost.getX(), ghost.getY(), ghost.getZ(), false);
            }
        }
    }
}
