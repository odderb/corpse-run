package com.corpserun.ghost;

import net.minecraft.core.GlobalPos;
import org.jetbrains.annotations.Nullable;

import java.util.Deque;

/** Server-side ghost state, implemented on {@link net.minecraft.server.level.ServerPlayer} by mixin. */
public interface GhostData {

    @Nullable
    CorpseLocation corpserun$getCorpse();

    /** Sets (or clears, with null) the corpse location; also updates the synced ghost flag. */
    void corpserun$setCorpse(@Nullable CorpseLocation corpse);

    boolean corpserun$isInRange();

    void corpserun$setInRange(boolean inRange);

    /** Server tick count of the last {@code /corpse reset}, or 0 if never. Not persisted. */
    long corpserun$getLastReset();

    void corpserun$setLastReset(long tick);

    /** Everywhere the living player has walked this life (saved); frozen into the corpse location on death. */
    TrailStack corpserun$getTrail();

    /** Server tick at which post-resurrect protection ends, or 0 if none. Not persisted. */
    long corpserun$getProtectedUntil();

    void corpserun$setProtectedUntil(long tick);

    /** This ghost's ethereal blocks, oldest first (saved, so they're cleaned up after a restart). */
    Deque<GlobalPos> corpserun$getEtherealBlocks();

    /** Footprint guidance while a ghost (not saved). */
    GhostGuide corpserun$getGuide();

    /** Where this player last stood on the ground while alive (saved); the corpse goes there on death. */
    @Nullable
    GroundPos corpserun$getLastGround();

    void corpserun$setLastGround(@Nullable GroundPos pos);

    /**
     * The ghost's current or last howl (not saved). Lives on the player entity, so it starts empty on every join,
     * respawn and world switch.
     */
    @Nullable
    GhostCall.Song corpserun$getSong();

    void corpserun$setSong(@Nullable GhostCall.Song song);
}
