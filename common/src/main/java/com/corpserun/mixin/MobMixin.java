package com.corpserun.mixin;

import com.corpserun.ghost.GhostManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Catch-all for target paths (retaliation, anger) that don't go through targeting conditions. */
@Mixin(Mob.class)
public abstract class MobMixin {

    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void corpserun$ignoreGhosts(@Nullable LivingEntity target, CallbackInfo ci) {
        if (GhostManager.isGhost(target)) {
            ci.cancel();
        }
    }
}
