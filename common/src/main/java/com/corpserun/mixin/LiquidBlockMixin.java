package com.corpserun.mixin;

import com.corpserun.ghost.GhostManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * What ghosts walk on when they walk on water and lava: a still (source) block of either is a full, solid block
 * for a ghost standing above it, so ghosts walk level with the shore instead of knee-deep in the fluid as on
 * vanilla's half-height "stable" fluid shape (what Striders stand on). Whether a ghost may stand on a fluid at all is
 * LivingEntityMixin#canStandOnFluid (not while sneaking or with its head under). Everyone else gets vanilla's shape.
 * <p>
 * Same rule as vanilla's: only once the ghost is above the block's top (so a swimming ghost isn't walled in by the
 * blocks around it), and only at the surface (no more of the same fluid above). The fluid is asked about straight
 * from the entity rather than through {@code context.canStandOnFluid}. Sable (Create Aeronautics) up to at least
 * 1.2.2 replaces the collision context with one that asks about the fluid above instead (a bug fixed in Sable but
 * not released yet), which would have ghosts sink.
 */
@Mixin(LiquidBlock.class)
public abstract class LiquidBlockMixin {

    @Inject(method = "getCollisionShape", at = @At("RETURN"), cancellable = true)
    private void corpserun$ghostsStandOnFluid(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context,
                                              CallbackInfoReturnable<VoxelShape> cir) {
        if (!(context instanceof EntityCollisionContext entityContext)
                || !(entityContext.getEntity() instanceof LivingEntity entity)
                || !GhostManager.isGhost(entity)) {
            return;
        }
        FluidState fluid = state.getFluidState();
        VoxelShape block = Shapes.block();
        boolean stand = state.getValue(LiquidBlock.LEVEL) == 0
                && context.isAbove(block, pos, true)
                && entity.canStandOnFluid(fluid)
                && !level.getFluidState(pos.above()).getType().isSame(fluid.getType());
        cir.setReturnValue(stand ? block : Shapes.empty());
    }
}
