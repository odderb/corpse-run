package com.corpserun.ghost;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** Marker effect shown while a player is corpse running. All behaviour lives in {@link GhostManager} and the mixins. */
public class GhostEffect extends MobEffect {

    public GhostEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xD8E4F0);
    }
}
