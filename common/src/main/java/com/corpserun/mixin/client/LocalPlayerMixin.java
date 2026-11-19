package com.corpserun.mixin.client;

import com.corpserun.ghost.GhostManager;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets a swimming ghost bob back up onto the water or lava it walks on: when its head breaks the surface of a still
 * one while moving up (and not sneaking), it gets a jump-sized boost that lifts its feet above the surface, and the fluid-walking
 * collision (LivingEntityMixin) catches it on the way down. Client-side, where the local player's movement is computed.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {

    /** Upward speed given on breaking the surface; vanilla's jump is 0.42. Lifts the feet about 1.6 blocks. */
    @Unique
    private static final double CORPSERUN$SURFACE_BOOST = 0.5;

    @Unique
    private boolean corpserun$eyeWasInFluid;

    /** aiStep runs after the eye-in-fluid state is updated for this tick, and before movement is applied. */
    @Inject(method = "aiStep", at = @At("HEAD"))
    private void corpserun$ghostSurfaceBoost(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        boolean eyeInFluid = player.isEyeInFluid(FluidTags.WATER) || player.isEyeInFluid(FluidTags.LAVA);
        boolean brokeSurface = this.corpserun$eyeWasInFluid && !eyeInFluid;
        this.corpserun$eyeWasInFluid = eyeInFluid;

        Vec3 motion = player.getDeltaMovement();
        if (!brokeSurface || motion.y <= 0.0 || player.isDescending() || !GhostManager.isGhost(player)) {
            return;
        }
        // Only where there's a still water or lava surface to land on: the block the head just left.
        BlockPos surface = BlockPos.containing(player.getX(), player.getEyeY() - 0.5, player.getZ());
        FluidState fluid = player.level().getFluidState(surface);
        if (GhostManager.walkableFluid(fluid) != null && fluid.isSource() && player.level().getFluidState(surface.above()).isEmpty()) {
            player.setDeltaMovement(motion.x, Math.max(motion.y, CORPSERUN$SURFACE_BOOST), motion.z);
        }
    }
}
