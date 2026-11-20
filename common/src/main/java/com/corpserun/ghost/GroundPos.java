package com.corpserun.ghost;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Where a living player last stood on solid ground; the corpse is laid there on death.
 *
 * @param plotPos the same spot in sub-level plot coordinates if it was on a Sable sub-level (an airship), so the
 *                corpse follows the structure; null on normal ground
 */
public record GroundPos(ResourceKey<Level> dimension, Vec3 pos, @Nullable Vec3 plotPos) {

    public GroundPos(ResourceKey<Level> dimension, Vec3 pos) {
        this(dimension, pos, null);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Dimension", dimension.location().toString());
        tag.putDouble("X", pos.x);
        tag.putDouble("Y", pos.y);
        tag.putDouble("Z", pos.z);
        PlotPos.save(tag, plotPos);
        return tag;
    }

    public static GroundPos load(CompoundTag tag) {
        return new GroundPos(
                ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("Dimension"))),
                new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z")),
                PlotPos.load(tag));
    }
}
