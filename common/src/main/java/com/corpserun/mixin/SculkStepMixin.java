package com.corpserun.mixin;

import com.corpserun.config.CorpseRunConfig;
import com.corpserun.ghost.GhostManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.block.SculkShriekerBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stepping on a sculk sensor or shrieker sets it off directly, without a game event (so ServerLevelMixin doesn't
 * catch it). With sculkIgnoresGhosts on, ghosts don't. Neither block does anything else in stepOn.
 */
@Mixin({SculkSensorBlock.class, SculkShriekerBlock.class})
public abstract class SculkStepMixin {

    @Inject(method = "stepOn", at = @At("HEAD"), cancellable = true)
    private void corpserun$ghostsDontTriggerSculk(Level level, BlockPos pos, BlockState state, Entity entity, CallbackInfo ci) {
        if (CorpseRunConfig.sculkIgnoresGhosts() && GhostManager.isGhost(entity)) {
            ci.cancel();
        }
    }
}
