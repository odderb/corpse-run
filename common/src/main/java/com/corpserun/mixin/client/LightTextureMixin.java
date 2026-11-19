package com.corpserun.mixin.client;

import com.corpserun.config.CorpseRunClientConfig;
import com.corpserun.ghost.GhostManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Ghosts see in the dark: a weaker, client-only night vision (strength from {@link CorpseRunClientConfig#ghostNightVision()})
 * without a real effect, so there's no effect icon, no fade-out flicker and nothing to sync.
 * <p>
 * Vanilla only computes a night-vision strength when the player has the effect, so the ghost is let into that branch,
 * and the strength there is raised to at least the configured value. Real Night Vision still wins when it's stronger.
 */
@Mixin(LightTexture.class)
public abstract class LightTextureMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @WrapOperation(method = "updateLightTexture", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;hasEffect(Lnet/minecraft/core/Holder;)Z", ordinal = 0))
    private boolean corpserun$ghostHasNightVision(LocalPlayer player, Holder<MobEffect> effect, Operation<Boolean> original) {
        return original.call(player, effect) || corpserun$ghostNightVision() > 0.0F;
    }

    @WrapOperation(method = "updateLightTexture", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;getNightVisionScale(Lnet/minecraft/world/entity/LivingEntity;F)F"))
    private float corpserun$ghostNightVisionScale(LivingEntity entity, float partialTicks, Operation<Float> original) {
        float ghost = corpserun$ghostNightVision();
        if (ghost <= 0.0F) {
            return original.call(entity, partialTicks);
        }
        // getNightVisionScale reads the effect instance, so only call it when there really is one.
        return entity.hasEffect(MobEffects.NIGHT_VISION) ? Math.max(ghost, original.call(entity, partialTicks)) : ghost;
    }

    /** The ghost night-vision strength for the local player right now, or 0 when they aren't a ghost. */
    private float corpserun$ghostNightVision() {
        return GhostManager.isGhost(this.minecraft.player) ? CorpseRunClientConfig.ghostNightVision() : 0.0F;
    }
}
