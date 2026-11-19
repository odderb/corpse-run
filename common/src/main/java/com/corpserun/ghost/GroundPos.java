package com.corpserun.ghost;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Where a living player last stood on solid ground; the corpse is laid there on death. */
public record GroundPos(ResourceKey<Level> dimension, Vec3 pos) {

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Dimension", dimension.location().toString());
        tag.putDouble("X", pos.x);
        tag.putDouble("Y", pos.y);
        tag.putDouble("Z", pos.z);
        return tag;
    }

    public static GroundPos load(CompoundTag tag) {
        return new GroundPos(
                ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("Dimension"))),
                new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z")));
    }
}
