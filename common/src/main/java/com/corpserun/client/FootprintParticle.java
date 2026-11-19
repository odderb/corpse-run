package com.corpserun.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * A ghost's guiding footprint: a full-bright dot that ignores world light (so it's just as bright at night and in
 * caves). It appears a block above the ground, sinks slowly onto it while drifting toward the next footprint (the
 * velocity the server sends), and fades out on the way down.
 */
public class FootprintParticle extends TextureSheetParticle {

    private static final int LIFETIME = 40;
    /** Fully opaque for this many ticks, then fades linearly to nothing. */
    private static final int SOLID_TICKS = 8;
    /** Sinks this far over its life: the server spawns it this high above the ground. */
    private static final double FALL_HEIGHT = 1.0;

    protected FootprintParticle(ClientLevel level, double x, double y, double z, double xSpeed, double zSpeed,
                                float size, SpriteSet sprites) {
        super(level, x, y, z);
        this.quadSize = size;
        this.lifetime = LIFETIME;
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.friction = 1.0F;   // steady sink and drift, no slowing down
        this.xd = xSpeed;
        this.yd = -FALL_HEIGHT / LIFETIME;
        this.zd = zSpeed;
        this.setColor(0.88F, 0.96F, 1.0F);
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        int fading = this.age - SOLID_TICKS;
        this.alpha = fading <= 0 ? 1.0F : Math.max(0.0F, 1.0F - fading / (float) (LIFETIME - SOLID_TICKS));
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
    }

    /** One provider per particle type, differing only in size (heel vs toe). */
    public static class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;
        private final float size;

        public Provider(SpriteSet sprites, float size) {
            this.sprites = sprites;
            this.size = size;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            return new FootprintParticle(level, x, y, z, xSpeed, zSpeed, this.size, this.sprites);
        }
    }

    /** Heel and toe sizes, about what the dust footprints were (quad half-size, in blocks). */
    public static final float HEEL_SIZE = 0.11F;
    public static final float TOE_SIZE = 0.17F;
}
