package com.corpserun.client;

import com.corpserun.entity.CorpseEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

/** Renders a corpse as its owner's player model lying on its back. */
public class CorpseRenderer extends EntityRenderer<CorpseEntity> {

    /** PlayerRenderer draws players at 15/16 of model size. */
    private static final float PLAYER_SCALE = 0.9375F;

    private final PlayerModel<LivingEntity> wideModel;
    private final PlayerModel<LivingEntity> slimModel;

    public CorpseRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.wideModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false);
        this.slimModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        this.shadowRadius = 0.4F;
    }

    @Override
    public void render(CorpseEntity corpse, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        PlayerSkin skin = skinOf(corpse);
        PlayerModel<LivingEntity> model = skin.model() == PlayerSkin.Model.SLIM ? this.slimModel : this.wideModel;

        // Models start out as "young" (baby proportions: big head, half-size body). Living-entity renderers reset
        // this every frame; this renderer has to do it itself.
        model.young = false;
        model.riding = false;

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - corpse.getYRot()));
        // Centre the body on the entity and raise it so the back rests on the ground, then lay it on its back.
        // The model is 2 blocks from feet to head top and 0.25 deep; at player scale that's 1.875 by 0.234.
        poseStack.translate(0.0F, 0.125F * PLAYER_SCALE, PLAYER_SCALE);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        poseStack.scale(PLAYER_SCALE, PLAYER_SCALE, PLAYER_SCALE);
        // Standard living-entity model transform.
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);

        model.renderToBuffer(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(skin.texture())),
                packedLight, OverlayTexture.NO_OVERLAY, -1);

        poseStack.popPose();
        super.render(corpse, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(CorpseEntity corpse) {
        return skinOf(corpse).texture();
    }

    private static PlayerSkin skinOf(CorpseEntity corpse) {
        UUID owner = corpse.getOwnerId();
        if (owner == null) {
            return DefaultPlayerSkin.get(corpse.getUUID());
        }
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection != null ? connection.getPlayerInfo(owner) : null;
        return info != null ? info.getSkin() : DefaultPlayerSkin.get(owner);
    }
}
