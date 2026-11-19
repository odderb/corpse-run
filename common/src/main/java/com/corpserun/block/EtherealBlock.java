package com.corpserun.block;

import com.corpserun.ghost.GhostManager;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Temporary block a ghost places to reach its body. Behaves like scaffolding without the support rules: stand on
 * top, climb inside (it's in {@code minecraft:climbable}), sneak to sink through. Unbreakable, drops nothing,
 * removed automatically by {@link EtherealBlocks}.
 * <p>
 * Only ghosts can see or touch it. For everyone and everything else it has no outline and no collision, can't be
 * climbed, and is replaced by any block placed into it; clients whose player isn't a ghost don't draw it.
 */
public class EtherealBlock extends Block {

    public static final MapCodec<EtherealBlock> CODEC = simpleCodec(EtherealBlock::new);

    /** How often (ticks) a block checks whether it's still wanted; see {@link #tick}. */
    private static final int STALE_CHECK_INTERVAL = 100;

    /** Scaffolding's stable shape: a top plate and four corner posts. */
    private static final VoxelShape STABLE_SHAPE = Shapes.or(
            Block.box(0.0, 14.0, 0.0, 16.0, 16.0, 16.0),
            Block.box(0.0, 0.0, 0.0, 2.0, 16.0, 2.0),
            Block.box(14.0, 0.0, 0.0, 16.0, 16.0, 2.0),
            Block.box(0.0, 0.0, 14.0, 2.0, 16.0, 16.0),
            Block.box(14.0, 0.0, 14.0, 16.0, 16.0, 16.0));

    /**
     * Whether this client draws ethereal blocks: true while the local player is a ghost. Read by chunk meshing
     * threads; {@code EtherealVisibility} flips it and rebuilds the chunks.
     */
    public static volatile boolean visible = false;

    public EtherealBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    public static BlockBehaviour.Properties defaultProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.SNOW)
                .strength(-1.0F, 3_600_000.0F)
                .noLootTable()
                .noOcclusion()
                .sound(SoundType.WOOL)
                .pushReaction(PushReaction.BLOCK)
                .isValidSpawn((state, level, pos, type) -> false)
                .isRedstoneConductor((state, level, pos) -> false)
                .isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos) -> false);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    /** True when the context belongs to a ghost. Entity-less contexts (mob pathfinding etc.) aren't ghosts. */
    public static boolean isGhostContext(CollisionContext context) {
        if (context instanceof EntityCollisionContext entityContext) {
            Entity entity = entityContext.getEntity();
            return entity != null && GhostManager.isGhost(entity);
        }
        return false;
    }

    /** Full-block outline for ghosts so faces are easy to target; none for anyone else, so clicks pass through. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return isGhostContext(context) ? Shapes.block() : Shapes.empty();
    }

    /**
     * For ghosts, as scaffolding: solid only from above and when not sneaking; otherwise you're inside it, climbing.
     * Everything else passes straight through.
     */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!isGhostContext(context)) {
            return Shapes.empty();
        }
        return context.isAbove(Shapes.block(), pos, true) && !context.isDescending() ? STABLE_SHAPE : Shapes.empty();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return visible ? RenderShape.MODEL : RenderShape.INVISIBLE;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && !oldState.is(this)) {
            level.scheduleTick(pos, this, STALE_CHECK_INTERVAL);
        }
    }

    /**
     * Removes the block if no ghost owns it any more (see EtherealBlockData), else checks again later. This is how
     * blocks released while their chunk was unloaded get cleared: scheduled ticks are saved with the chunk and run
     * once it's loaded and ticking again.
     */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (EtherealBlockData.get(level.getServer()).contains(GlobalPos.of(level.dimension(), pos))) {
            level.scheduleTick(pos, this, STALE_CHECK_INTERVAL);
        } else {
            level.removeBlock(pos, false);
        }
    }

    /** Replaced or cleared: it's no longer an ethereal block anyone needs to track. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (level instanceof ServerLevel serverLevel && !newState.is(this)) {
            EtherealBlockData.get(serverLevel.getServer()).remove(GlobalPos.of(serverLevel.dimension(), pos));
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Non-ghosts build into the space as if it were air; ghosts can't overwrite their own blocks. */
    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return context.getPlayer() == null || !GhostManager.isGhost(context.getPlayer());
    }

    /** Don't draw faces between neighbouring ethereal blocks (like glass). */
    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
        return adjacent.is(this) || super.skipRendering(state, adjacent, direction);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    /**
     * NeoForge's IBlockExtension hook (a plain method on Fabric, where a mixin covers the same thing): sneaking
     * inside slides a ghost down like scaffolding instead of holding position like a ladder.
     */
    public boolean isScaffolding(BlockState state, LevelReader level, BlockPos pos, LivingEntity entity) {
        return GhostManager.isGhost(entity);
    }
}
