package com.corpserun.client.ghost;

import com.corpserun.Constants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

/**
 * Draws a ghost player with {@link GhostModel} instead of the player model. Called from PlayerRendererMixin, which
 * skips the normal player render (so armor, held items, capes etc. aren't drawn) and draws the name tag.
 */
public final class GhostPlayerRenderer {

    public static final ResourceLocation TEXTURE = Constants.id("textures/entity/tex_ghostie.png");
    /** Model opacity, 0..255. */
    private static final int ALPHA = 150;
    /** The name tag sits this far below where a player's would, to stay just above the smaller model. */
    public static final float NAME_TAG_DROP = 0.2F;

    private GhostPlayerRenderer() {
    }

    public static void render(GhostModel model, AbstractClientPlayer player, float partialTick, PoseStack poseStack,
                              MultiBufferSource buffers, int packedLight) {
        poseStack.pushPose();
        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        // Standard living-entity model transform: model space is y-down with the ground at y = 24 (1.5 blocks).
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);

        float headYaw = Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot) - bodyYaw;
        float headPitch = Mth.lerp(partialTick, player.xRotO, player.getXRot());
        float limbSwing = player.walkAnimation.position(partialTick);
        float limbSwingAmount = Math.min(player.walkAnimation.speed(partialTick), 1.0F);
        model.setupAnim(player, limbSwing, limbSwingAmount, player.tickCount + partialTick, headYaw, headPitch);

        model.renderToBuffer(poseStack, buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)), packedLight,
                OverlayTexture.NO_OVERLAY, FastColor.ARGB32.color(ALPHA, 255, 255, 255));
        poseStack.popPose();
    }
}
