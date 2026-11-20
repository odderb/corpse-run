package com.corpserun.ghost;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Where a ghost's body lies, plus the trail stack frozen at death (everywhere the player walked that life, ending
 * at the corpse). The corpse entity is cosmetic; this record is what range checks and guidance use.
 *
 * @param pos     the body's world position; for a corpse on a sub-level, where it was last seen (kept up to date
 *                while the sub-level is loaded, see {@link GhostManager})
 * @param plotPos the body's position in sub-level plot coordinates if it died on a Sable sub-level (an airship),
 *                so it moves with the structure; null on normal ground
 */
public record CorpseLocation(UUID corpseId, ResourceKey<Level> dimension, Vec3 pos, @Nullable Vec3 plotPos,
                             TrailStack trail) {

    public CorpseLocation withPos(Vec3 newPos) {
        return new CorpseLocation(corpseId, dimension, newPos, plotPos, trail);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("CorpseId", corpseId);
        tag.putString("Dimension", dimension.location().toString());
        tag.putDouble("X", pos.x);
        tag.putDouble("Y", pos.y);
        tag.putDouble("Z", pos.z);
        PlotPos.save(tag, plotPos);
        tag.put("Trail", trail.save());
        return tag;
    }

    public static CorpseLocation load(CompoundTag tag) {
        return new CorpseLocation(
                tag.getUUID("CorpseId"),
                ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("Dimension"))),
                new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z")),
                PlotPos.load(tag),
                TrailStack.load(tag, "Trail"));
    }
}
