package com.corpserun.ghost;

/**
 * Implemented on {@link net.minecraft.world.entity.player.Player} by mixin. The ghost flag is stored in synched
 * entity data so every client knows which players are ghosts (needed for rendering and client-side prediction).
 */
public interface GhostPlayer {

    boolean corpserun$isGhost();

    void corpserun$setGhost(boolean ghost);
}
