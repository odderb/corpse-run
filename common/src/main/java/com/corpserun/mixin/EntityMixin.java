package com.corpserun.mixin;

import com.corpserun.ghost.GhostManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.gameevent.GameEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ghosts are fire immune, so walking on or swimming through lava (LivingEntityMixin) never sets them alight: no
 * lava damage, no burning, no flames drawn on them or over their screen. Any fire they had when they died burns out.
 * They can also jump in shallow fluid. Read on both sides; the client knows ghost state from synced entity data.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {

    /**
     * Ghosts make no sound walking on amethyst or swimming (see PlayerStepSoundMixin). Cancelled on both sides, like
     * footsteps.
     */
    @Inject(method = {"playAmethystStepSound", "playSwimSound"}, at = @At("HEAD"), cancellable = true)
    private void corpserun$ghostsMoveSilently(CallbackInfo ci) {
        if (GhostManager.isGhost((Entity) (Object) this)) {
            ci.cancel();
        }
    }

    /**
     * Ghosts splash into water without a sound or splash particles. The splash game event is still posted, so sculk
     * notices it when sculkIgnoresGhosts is off (ServerLevelMixin drops it otherwise).
     */
    @Inject(method = "doWaterSplashEffect", at = @At("HEAD"), cancellable = true)
    private void corpserun$ghostsSplashSilently(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (GhostManager.isGhost(self)) {
            ci.cancel();
            self.gameEvent(GameEvent.SPLASH);
        }
    }

    @Inject(method = "fireImmune", at = @At("HEAD"), cancellable = true)
    private void corpserun$ghostsFireImmune(CallbackInfoReturnable<Boolean> cir) {
        if (GhostManager.isGhost((Entity) (Object) this)) {
            cir.setReturnValue(true);
        }
    }

    /**
     * Lets a ghost jump while standing in shallow (e.g. 1 block deep) water or lava. With its head out,
     * LivingEntityMixin#canStandOnFluid already gives it land physics, but LivingEntity#aiStep's jump still sees
     * fluid around the body deeper than this threshold and "swims up" instead: a tiny push that land gravity
     * cancels, so it couldn't jump. A threshold no fluid reaches makes it take the normal jump. Only while on the
     * ground and standing in fluid it may stand on, so swimming is untouched. Besides the jump, this is only read by
     * mob AI and the lava swimming branch of travel(), which canStandOnFluid already skips in this state.
     */
    @Inject(method = "getFluidJumpThreshold", at = @At("HEAD"), cancellable = true)
    private void corpserun$ghostsJumpInShallowFluid(CallbackInfoReturnable<Double> cir) {
        if ((Object) this instanceof LivingEntity self && GhostManager.isGhost(self) && self.onGround()
                && self.canStandOnFluid(self.level().getFluidState(self.blockPosition()))) {
            cir.setReturnValue(Double.MAX_VALUE);
        }
    }
}
