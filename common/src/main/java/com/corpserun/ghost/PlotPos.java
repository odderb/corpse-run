package com.corpserun.ghost;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** NBT for an optional sub-level plot position (see {@link com.corpserun.compat.SubLevels}). */
public final class PlotPos {

    private static final String KEY = "Plot";

    private PlotPos() {
    }

    public static void save(CompoundTag tag, @Nullable Vec3 plotPos) {
        if (plotPos != null) {
            CompoundTag plot = new CompoundTag();
            plot.putDouble("X", plotPos.x);
            plot.putDouble("Y", plotPos.y);
            plot.putDouble("Z", plotPos.z);
            tag.put(KEY, plot);
        }
    }

    @Nullable
    public static Vec3 load(CompoundTag tag) {
        if (!tag.contains(KEY, CompoundTag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag plot = tag.getCompound(KEY);
        return new Vec3(plot.getDouble("X"), plot.getDouble("Y"), plot.getDouble("Z"));
    }
}
