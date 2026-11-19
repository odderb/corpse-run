package com.corpserun.mixin;

import com.corpserun.ghost.GhostManager;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ghosts are fire immune, so walking on or swimming through lava (LivingEntityMixin) never sets them alight: no
 * lava damage, no burning, no flames drawn on them or over their screen. Any fire they had when they died burns out.
 * Read on both sides; the client knows ghost state from synced entity data.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {

    @Inject(method = "fireImmune", at = @At("HEAD"), cancellable = true)
    private void corpserun$ghostsFireImmune(CallbackInfoReturnable<Boolean> cir) {
        if (GhostManager.isGhost((Entity) (Object) this)) {
            cir.setReturnValue(true);
        }
    }
}
