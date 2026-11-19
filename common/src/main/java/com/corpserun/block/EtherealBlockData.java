package com.corpserun.block;

import com.corpserun.Constants;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Every ethereal block that still belongs to a ghost, across all dimensions (saved with the world). A block whose
 * position isn't in here is stale: its ghost resurrected or it was evicted while its chunk wasn't loaded, so it
 * couldn't be cleared then. Such blocks remove themselves on their next scheduled tick (see EtherealBlock#tick),
 * which means cleanup never has to load a chunk.
 */
public final class EtherealBlockData extends SavedData {

    private static final String NAME = Constants.MOD_ID + "_ethereal_blocks";

    private final Set<GlobalPos> positions = new HashSet<>();

    public static EtherealBlockData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(EtherealBlockData::new, EtherealBlockData::load, null), NAME);
    }

    public boolean contains(GlobalPos pos) {
        return this.positions.contains(pos);
    }

    public void add(GlobalPos pos) {
        if (this.positions.add(pos)) {
            setDirty();
        }
    }

    public void remove(GlobalPos pos) {
        if (this.positions.remove(pos)) {
            setDirty();
        }
    }

    private static EtherealBlockData load(CompoundTag tag, HolderLookup.Provider registries) {
        EtherealBlockData data = new EtherealBlockData();
        Deque<GlobalPos> loaded = new ArrayDeque<>();
        EtherealBlocks.load(tag, "Positions", loaded);
        data.positions.addAll(loaded);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Positions", EtherealBlocks.save(this.positions));
        return tag;
    }
}
