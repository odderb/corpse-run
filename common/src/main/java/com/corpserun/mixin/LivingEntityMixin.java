package com.corpserun.mixin;

import com.corpserun.CorpseRun;
import com.corpserun.ghost.GhostManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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
     * Ghosts walk on water and lava: a still (source) block of either is then a full solid block under them (see
     * LiquidBlockMixin). Flowing fluid stays swimmable. Sneaking drops the ghost in to swim;
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

    /**
     * Invigorated players' melee hits knock harder and slow what they hit (GhostManager#invigoratedKnockbackBonus,
     * #onInvigoratedHit). Player#attack only asks for the knockback once a hit has landed (sweep hits too), so this
     * is also where a hit is known to have landed.
     */
    @Inject(method = "getKnockback", at = @At("RETURN"), cancellable = true)
    private void corpserun$invigoratedHit(Entity target, DamageSource source, CallbackInfoReturnable<Float> cir) {
        if (!((Object) this instanceof ServerPlayer player)) {
            return;
        }
        float bonus = GhostManager.invigoratedKnockbackBonus(player);
        if (bonus > 0.0F) {
            cir.setReturnValue(cir.getReturnValueF() + bonus);
        }
        if (target instanceof LivingEntity living) {
            GhostManager.onInvigoratedHit(player, living);
        }
    }

    /**
     * Ghosts land silently: no landing thud or fall damage sound. Those only play when a fall would deal damage, and
     * ghosts take none (ServerPlayerMixin), so the whole call is skipped. Runs on both sides, since the falling
     * player's own client plays these sounds locally.
     */
    @Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
    private void corpserun$ghostsLandSilently(float fallDistance, float multiplier, DamageSource source,
                                              CallbackInfoReturnable<Boolean> cir) {
        if (GhostManager.isGhost((LivingEntity) (Object) this)) {
            cir.setReturnValue(false);
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
