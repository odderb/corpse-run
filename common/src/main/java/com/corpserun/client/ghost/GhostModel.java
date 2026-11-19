package com.corpserun.client.ghost;

import com.corpserun.Constants;
import com.corpserun.client.bedrock.BedrockAnimations;
import com.corpserun.client.bedrock.BedrockGeometry;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * The model a ghost player is drawn with instead of the player model, loaded from Blockbench's Bedrock exports:
 * <ul>
 *   <li>{@code assets/corpserun/geo/ghostie.geo.json}: the geometry</li>
 *   <li>{@code assets/corpserun/animations/ghostie.animation.json}: animations; the one named {@code float} (or
 *       {@code animation.<anything>.float}) loops for as long as the player is a ghost</li>
 *   <li>{@code assets/corpserun/textures/entity/tex_ghostie.png}: the texture</li>
 * </ul>
 * Loaded whenever the player renderers are built, so F3+T picks up new exports without a restart. A broken or
 * missing geometry file logs an error and shows a plain cube instead.
 */
public class GhostModel extends HierarchicalModel<AbstractClientPlayer> {

    public static final ResourceLocation GEOMETRY = Constants.id("geo/ghostie.geo.json");
    public static final ResourceLocation ANIMATIONS = Constants.id("animations/ghostie.animation.json");
    public static final String FLOAT_ANIMATION = "float";

    private static final Vector3f ANIMATION_VECTOR_CACHE = new Vector3f();

    private final ModelPart root;
    @Nullable
    private final AnimationDefinition floatAnimation;

    public GhostModel(ModelPart root, @Nullable AnimationDefinition floatAnimation) {
        this.root = root;
        this.floatAnimation = floatAnimation;
    }

    public static GhostModel load(ResourceManager resources) {
        ModelPart root;
        try {
            root = BedrockGeometry.load(resources, GEOMETRY);
        } catch (Exception e) {
            Constants.LOG.error("Couldn't load the ghost model {}, using a plain cube", GEOMETRY, e);
            root = fallback();
        }

        AnimationDefinition floatAnimation = null;
        try {
            floatAnimation = BedrockAnimations.load(resources, ANIMATIONS, FLOAT_ANIMATION);
            if (floatAnimation == null) {
                Constants.LOG.warn("{} has no animation named '{}'; the ghost won't animate", ANIMATIONS, FLOAT_ANIMATION);
            }
        } catch (Exception e) {
            Constants.LOG.error("Couldn't load the ghost animations {}", ANIMATIONS, e);
        }
        return new GhostModel(root, floatAnimation);
    }

    private static ModelPart fallback() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0F, -16.0F, -4.0F, 8.0F, 8.0F, 8.0F), PartPose.offset(0.0F, 24.0F, 0.0F));
        return LayerDefinition.create(mesh, 32, 32).bakeRoot();
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);
        if (this.floatAnimation != null) {
            // A looping animation only needs the time since the player was created (no AnimationState).
            long elapsedMillis = (long) (ageInTicks * 50.0F);
            KeyframeAnimations.animate(this, this.floatAnimation, elapsedMillis, 1.0F, ANIMATION_VECTOR_CACHE);
        }
    }
}
