package com.corpserun.ghost;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Marker effect shown for the post-resurrect protection window (invulnerable, extra knockback, hits slow their target). The protection itself
 * is timed and applied by {@link GhostManager}; this only makes it visible, with the remaining time.
 */
public class InvigoratedEffect extends MobEffect {

    public InvigoratedEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xF2C14E);
    }
}
