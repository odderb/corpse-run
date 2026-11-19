package com.corpserun.mixin.client;

import com.corpserun.client.ghost.GhostModel;
import com.corpserun.client.ghost.GhostPlayerRenderer;
import com.corpserun.ghost.GhostManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ghost players are drawn with GhostModel instead of the player model, and show no arm in first person. */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    @Unique
    private GhostModel corpserun$ghostModel;

    private PlayerRendererMixin(EntityRendererProvider.Context context, PlayerModel<AbstractClientPlayer> model, float shadowRadius) {
        super(context, model, shadowRadius);
    }

    /** Renderers are rebuilt on every resource reload (F3+T included), so the Bedrock files are re-read with them. */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void corpserun$bakeGhostModel(EntityRendererProvider.Context context, boolean slim, CallbackInfo ci) {
        this.corpserun$ghostModel = GhostModel.load(context.getResourceManager());
    }

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true)
    private void corpserun$renderGhost(AbstractClientPlayer player, float entityYaw, float partialTick, PoseStack poseStack,
                                       MultiBufferSource buffers, int packedLight, CallbackInfo ci) {
        if (!GhostManager.isGhost(player)) {
            return;
        }
        ci.cancel();
        GhostPlayerRenderer.render(this.corpserun$ghostModel, player, partialTick, poseStack, buffers, packedLight);
        if (this.shouldShowName(player)) {
            poseStack.pushPose();
            poseStack.translate(0.0F, -GhostPlayerRenderer.NAME_TAG_DROP, 0.0F);
            this.renderNameTag(player, player.getDisplayName(), poseStack, buffers, packedLight, partialTick);
            poseStack.popPose();
        }
    }

    @Inject(method = {"renderRightHand", "renderLeftHand"}, at = @At("HEAD"), cancellable = true)
    private void corpserun$noGhostHand(PoseStack poseStack, MultiBufferSource buffers, int packedLight,
                                       AbstractClientPlayer player, CallbackInfo ci) {
        if (GhostManager.isGhost(player)) {
            ci.cancel();
        }
    }
}
