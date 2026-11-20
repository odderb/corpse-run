package com.corpserun.compat;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Sable sub-levels (moving physics structures, e.g. Create Aeronautics airships), through Sable Companion. Without
 * Sable installed the companion's default implementation reports no sub-levels, so everything here is a no-op.
 * <p>
 * A sub-level's blocks live in its "plot", a fixed region of the level; its pose maps plot coordinates to where
 * the structure currently is in the world. A position stored in plot coordinates therefore moves with the
 * sub-level, which is how corpses are parented to airships.
 */
public final class SubLevels {

    private SubLevels() {
    }

    /**
     * The plot-space position of {@code entity}'s feet if it is standing on (or riding something on) a sub-level,
     * otherwise null.
     */
    @Nullable
    public static Vec3 toPlot(Entity entity) {
        SubLevelAccess subLevel = standingOn(entity);
        return subLevel == null ? null : subLevel.logicalPose().transformPositionInverse(entity.position());
    }

    /** Where a plot position currently is in the world, or null if no sub-level is there (unloaded or destroyed). */
    @Nullable
    public static Vec3 toWorld(Level level, Vec3 plotPos) {
        SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(level, plotPos);
        return subLevel == null ? null : subLevel.logicalPose().transformPosition(plotPos);
    }

    /** A world yaw turned into the frame of the sub-level {@code entity} stands on (unchanged if none). */
    public static float toPlotYaw(Entity entity, float yaw) {
        SubLevelAccess subLevel = standingOn(entity);
        if (subLevel == null) {
            return yaw;
        }
        return yawOf(subLevel.logicalPose().transformNormalInverse(forward(yaw)), yaw);
    }

    /** A plot-frame yaw turned into the world frame by the sub-level at {@code plotPos} (unchanged if none). */
    public static float toWorldYaw(Level level, Vec3 plotPos, float plotYaw) {
        SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(level, plotPos);
        if (subLevel == null) {
            return plotYaw;
        }
        return yawOf(subLevel.logicalPose().transformNormal(forward(plotYaw)), plotYaw);
    }

    @Nullable
    private static SubLevelAccess standingOn(Entity entity) {
        SubLevelAccess subLevel = SableCompanion.INSTANCE.getTrackingOrVehicleSubLevel(entity);
        if (subLevel == null && entity.getVehicle() != null) {
            subLevel = SableCompanion.INSTANCE.getTrackingOrVehicleSubLevel(entity.getRootVehicle());
        }
        return subLevel;
    }

    /** Minecraft's facing vector for a yaw (0 = +Z, 90 = -X). */
    private static Vec3 forward(float yaw) {
        float rad = yaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(rad), 0.0, Mth.cos(rad));
    }

    /** The yaw of a direction, ignoring its vertical part; {@code fallback} if it points straight up or down. */
    private static float yawOf(Vec3 dir, float fallback) {
        if (dir.x * dir.x + dir.z * dir.z < 1.0E-6) {
            return fallback;
        }
        return (float) (Mth.atan2(-dir.x, dir.z) * Mth.RAD_TO_DEG);
    }
}
