package com.corpserun.mixin.client;

import com.corpserun.CorpseRun;
import com.corpserun.ghost.GhostManager;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client side of ghost block and item use, mirroring ServerPlayerGameModeMixin so nothing is mispredicted.
 * performUseItemOn is the local prediction run inside useItemOn; the use packet is still sent afterwards. An empty
 * main hand on a block ghosts can't use reports success, so the packet goes out and the server places an ethereal
 * block.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {

    /** No item use in the air: nothing is predicted and no packet is sent. */
    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void corpserun$ghostCannotUseItems(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (GhostManager.isGhost(player)) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    @Inject(method = "performUseItemOn", at = @At("HEAD"), cancellable = true)
    private void corpserun$ghostUseBlock(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
                                        CallbackInfoReturnable<InteractionResult> cir) {
        if (!GhostManager.isGhost(player) || CorpseRun.ghostCanUse(player.level().getBlockState(hit.getBlockPos()))) {
            return;   // doors, trapdoors, gates, buttons, levers: used as with an empty hand (below)
        }
        boolean placing = hand == InteractionHand.MAIN_HAND && player.getMainHandItem().isEmpty();
        cir.setReturnValue(placing ? InteractionResult.SUCCESS : InteractionResult.FAIL);
    }

    /** Ghosts predict block use as with an empty hand, like the server handles it. */
    @ModifyVariable(method = "performUseItemOn", at = @At("STORE"), ordinal = 0)
    private ItemStack corpserun$ghostsHoldNothing(ItemStack stack, LocalPlayer player) {
        return GhostManager.isGhost(player) ? ItemStack.EMPTY : stack;
    }
}
