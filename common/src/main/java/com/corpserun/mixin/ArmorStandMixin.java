package com.corpserun.mixin;

import com.corpserun.ghost.GhostManager;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Armor stands are used through interactAt, which skips Player.interactOn (blocked in PlayerMixin): ghosts can't
 * take or put items on them either.
 */
@Mixin(ArmorStand.class)
public abstract class ArmorStandMixin {

    @Inject(method = "interactAt", at = @At("HEAD"), cancellable = true)
    private void corpserun$ghostCannotUse(Player player, Vec3 vec, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (GhostManager.isGhost(player)) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
