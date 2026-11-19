package com.corpserun.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * A note floating out of a singing ghost's mouth. The texture is white; each note is tinted one of {@link #COLORS}
 * at random. It drifts along the velocity the server gives it (out of the mouth, slightly up), sways side to side,
 * and fades out. Full-bright like the footprints.
 */
public class SingingParticle extends TextureSheetParticle {

    /** #d6658f, #71ba1a, #3cb0da. */
    private static final int[] COLORS = {0xD6658F, 0x71BA1A, 0x3CB0DA};

    private static final int LIFETIME = 30;
    private static final int FADE_TICKS = 12;
    private static final float SIZE = 0.12F;
    /** Side-to-side sway, in blocks per tick. */
    private static final double SWAY = 0.012;

    private final float swayPhase;

    protected SingingParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed,
                              SpriteSet sprites) {
        super(level, x, y, z);
        this.quadSize = SIZE;
        this.lifetime = LIFETIME;
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.friction = 0.96F;
        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;
        this.swayPhase = this.random.nextFloat() * Mth.TWO_PI;
        int color = COLORS[this.random.nextInt(COLORS.length)];
        this.setColor(((color >> 16) & 0xFF) / 255.0F, ((color >> 8) & 0xFF) / 255.0F, (color & 0xFF) / 255.0F);
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        // Sway perpendicular to the direction of travel.
        double sway = Math.sin(this.age * 0.35 + this.swayPhase) * SWAY;
        double length = Math.sqrt(this.xd * this.xd + this.zd * this.zd);
        if (length > 1.0E-4) {
            this.x += -this.zd / length * sway;
            this.z += this.xd / length * sway;
            this.setPos(this.x, this.y, this.z);
        }
        int left = this.lifetime - this.age;
        this.alpha = left < FADE_TICKS ? Math.max(0.0F, left / (float) FADE_TICKS) : 1.0F;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            return new SingingParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites);
        }
    }
}
