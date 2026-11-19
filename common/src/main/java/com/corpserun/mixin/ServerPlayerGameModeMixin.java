package com.corpserun.mixin;

import com.corpserun.CorpseRun;
import com.corpserun.block.EtherealBlocks;
import com.corpserun.ghost.GhostManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ghosts can't use items at all, and can't use most blocks: no placing, no chests, furnaces, beds... Exceptions:
 * doors, trapdoors, fence gates, buttons and levers work as with an empty hand (see CorpseRun.ghostCanUse), and
 * right-clicking any other face with an empty main hand places an ethereal block.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {

    /** Using an item in the air (eating, pearls, bows, potions, fireworks...). */
    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void corpserun$ghostCannotUseItems(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand,
                                               CallbackInfoReturnable<InteractionResult> cir) {
        if (GhostManager.isGhost(player)) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void corpserun$ghostUseBlock(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand,
                                        BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (!GhostManager.isGhost(player) || CorpseRun.ghostCanUse(level.getBlockState(hit.getBlockPos()))) {
            return;
        }
        // The held item, not the stack argument (which corpserun$ghostsHoldNothing empties for ghosts).
        boolean emptyHand = player.getItemInHand(hand).isEmpty();
        if (hand == InteractionHand.MAIN_HAND && emptyHand && level instanceof ServerLevel serverLevel) {
            cir.setReturnValue(EtherealBlocks.tryPlace(player, serverLevel, hit));
        } else {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }

    /**
     * For the blocks ghosts may use, the rest of useItemOn runs as usual (so loader events and protection mods still
     * apply) but with an empty stack: the block is used as with an empty hand and the held item can never be used,
     * not even by sneaking or on a block that doesn't react (iron doors).
     */
    @ModifyVariable(method = "useItemOn", at = @At("HEAD"), argsOnly = true)
    private ItemStack corpserun$ghostsHoldNothing(ItemStack stack, ServerPlayer player) {
        return GhostManager.isGhost(player) ? ItemStack.EMPTY : stack;
    }
}
