package com.corpserun.ghost;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Where a ghost's body lies, plus the trail stack frozen at death (everywhere the player walked that life, ending
 * at the corpse). The corpse entity is cosmetic; this record is what range checks and guidance use.
 */
public record CorpseLocation(UUID corpseId, ResourceKey<Level> dimension, Vec3 pos, TrailStack trail) {

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("CorpseId", corpseId);
        tag.putString("Dimension", dimension.location().toString());
        tag.putDouble("X", pos.x);
        tag.putDouble("Y", pos.y);
        tag.putDouble("Z", pos.z);
        tag.put("Trail", trail.save());
        return tag;
    }

    public static CorpseLocation load(CompoundTag tag) {
        return new CorpseLocation(
                tag.getUUID("CorpseId"),
                ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("Dimension"))),
                new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z")),
                TrailStack.load(tag, "Trail"));
    }
}
