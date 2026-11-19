package com.corpserun.mixin;

import com.corpserun.CorpseRun;
import com.corpserun.block.EtherealBlocks;
import com.corpserun.ghost.GhostManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ghosts can't deal damage (melee, owned projectiles, thorns...), walk on still water and lava, and are the only ones who can
 * climb ethereal blocks.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void corpserun$ghostDealsNoDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (GhostManager.isGhost(source.getEntity())) {
            cir.setReturnValue(false);
        }
    }

    /**
     * Ghosts walk on water and lava like Striders on lava: vanilla's fluid collision then treats the surface of a
     * still (source) block of either as solid. Flowing fluid stays swimmable. Sneaking drops the ghost in to swim;
     * this is read at every collision check, so it takes effect as soon as shift is held. Ghosts are fire immune
     * (EntityMixin), so lava never burns them.
     * <p>
     * Only while the ghost's head is above that fluid: travel() also asks this, and a "yes" there swaps fluid physics
     * for land physics (full gravity, no swimming up), which would pin a submerged ghost to the bottom.
     */
    @Inject(method = "canStandOnFluid", at = @At("HEAD"), cancellable = true)
    private void corpserun$ghostsWalkOnFluids(FluidState fluid, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        TagKey<Fluid> tag = GhostManager.walkableFluid(fluid);
        if (tag != null && GhostManager.isGhost(self) && !self.isDescending() && !self.isEyeInFluid(tag)) {
            cir.setReturnValue(true);
        }
    }

    /** Landing on an ethereal block: only heard by players who can see ghosts (see PlayerStepSoundMixin). */
    @Inject(method = "playBlockFallSound", at = @At("HEAD"), cancellable = true)
    private void corpserun$etherealLandingGhostOnly(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof ServerPlayer ghost) || !GhostManager.isGhost(ghost)) {
            return;
        }
        BlockPos below = BlockPos.containing(ghost.getX(), ghost.getY() - 0.2, ghost.getZ());
        BlockState state = ghost.level().getBlockState(below);
        if (state.is(CorpseRun.ETHEREAL_BLOCK.get())) {
            ci.cancel();
            if (!ghost.isSilent()) {
                SoundType sound = state.getSoundType();
                EtherealBlocks.playForGhostViewers(ghost, sound.getFallSound(), sound.getVolume() * 0.5F, sound.getPitch() * 0.75F,
                        ghost.getX(), ghost.getY(), ghost.getZ(), false);
            }
        }
    }

    /** Ethereal blocks are in minecraft:climbable for ghosts' sake; nobody else gets to climb them. */
    @Inject(method = "onClimbable", at = @At("RETURN"), cancellable = true)
    private void corpserun$etherealClimbableOnlyForGhosts(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (cir.getReturnValueZ() && !GhostManager.isGhost(self) && self.getInBlockState().is(CorpseRun.ETHEREAL_BLOCK.get())) {
            cir.setReturnValue(false);
        }
    }
}
