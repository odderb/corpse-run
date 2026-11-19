package com.corpserun.mixin;

import com.corpserun.CorpseRun;
import com.corpserun.block.EtherealBlocks;
import com.corpserun.ghost.CorpseLocation;
import com.corpserun.ghost.GhostCall;
import com.corpserun.ghost.GhostData;
import com.corpserun.ghost.GhostGuide;
import com.corpserun.ghost.GhostManager;
import com.corpserun.ghost.GhostPlayer;
import com.corpserun.ghost.GroundPos;
import com.corpserun.ghost.TrailStack;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Deque;

/**
 * Stores and persists ghost state on the player. Lifecycle (death, respawn copy, ticking) comes from loader events
 * via {@link com.corpserun.CorpseRunEvents}.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin implements GhostData {

    @Unique
    private static final String CORPSERUN$TAG = "corpserun";

    @Unique
    @Nullable
    private CorpseLocation corpserun$corpse;
    @Unique
    private boolean corpserun$inRange;
    @Unique
    private long corpserun$lastReset;
    @Unique
    private long corpserun$protectedUntil;
    // Created lazily in the getters rather than with field initialisers, so they never depend on Mixin
    // merging initialisers into ServerPlayer's constructor.
    @Unique
    @Nullable
    private TrailStack corpserun$trail;
    @Unique
    @Nullable
    private GhostGuide corpserun$guide;
    @Unique
    @Nullable
    private Deque<GlobalPos> corpserun$etherealBlocks;
    @Unique
    @Nullable
    private GroundPos corpserun$lastGround;
    @Unique
    @Nullable
    private GhostCall.Song corpserun$song;

    @Override
    @Nullable
    public GroundPos corpserun$getLastGround() {
        return this.corpserun$lastGround;
    }

    @Override
    public void corpserun$setLastGround(@Nullable GroundPos pos) {
        this.corpserun$lastGround = pos;
    }

    @Override
    @Nullable
    public GhostCall.Song corpserun$getSong() {
        return this.corpserun$song;
    }

    @Override
    public void corpserun$setSong(@Nullable GhostCall.Song song) {
        this.corpserun$song = song;
    }

    @Override
    @Nullable
    public CorpseLocation corpserun$getCorpse() {
        return this.corpserun$corpse;
    }

    @Override
    public void corpserun$setCorpse(@Nullable CorpseLocation corpse) {
        this.corpserun$corpse = corpse;
        ((GhostPlayer) this).corpserun$setGhost(corpse != null);
    }

    @Override
    public boolean corpserun$isInRange() {
        return this.corpserun$inRange;
    }

    @Override
    public void corpserun$setInRange(boolean inRange) {
        this.corpserun$inRange = inRange;
    }

    @Override
    public long corpserun$getLastReset() {
        return this.corpserun$lastReset;
    }

    @Override
    public void corpserun$setLastReset(long tick) {
        this.corpserun$lastReset = tick;
    }

    @Override
    public long corpserun$getProtectedUntil() {
        return this.corpserun$protectedUntil;
    }

    @Override
    public void corpserun$setProtectedUntil(long tick) {
        this.corpserun$protectedUntil = tick;
    }

    @Override
    public TrailStack corpserun$getTrail() {
        if (this.corpserun$trail == null) {
            this.corpserun$trail = new TrailStack();
        }
        return this.corpserun$trail;
    }

    @Override
    public Deque<GlobalPos> corpserun$getEtherealBlocks() {
        if (this.corpserun$etherealBlocks == null) {
            this.corpserun$etherealBlocks = EtherealBlocks.newDeque();
        }
        return this.corpserun$etherealBlocks;
    }

    @Override
    public GhostGuide corpserun$getGuide() {
        if (this.corpserun$guide == null) {
            this.corpserun$guide = new GhostGuide();
        }
        return this.corpserun$guide;
    }

    /*
     * Lifecycle hooks. These are mixins rather than loader events so NeoForge and Fabric run exactly the same code
     * at exactly the same point (Fabric has no per-player tick event, and the loaders' death events fire at
     * different points of die()).
     */

    /** Reached only for a death that happened: totems and loader-cancelled deaths return earlier. */
    @Inject(method = "die", at = @At("TAIL"))
    private void corpserun$onDeath(DamageSource source, CallbackInfo ci) {
        GhostManager.onDeath((ServerPlayer) (Object) this);
    }

    /** Respawn or End exit: this is the new player entity, copying from the old one. */
    @Inject(method = "restoreFrom", at = @At("TAIL"))
    private void corpserun$onClone(ServerPlayer oldPlayer, boolean keepEverything, CallbackInfo ci) {
        GhostManager.onClone(oldPlayer, (ServerPlayer) (Object) this);
    }

    @Inject(method = "doTick", at = @At("TAIL"))
    private void corpserun$onTick(CallbackInfo ci) {
        GhostManager.tick((ServerPlayer) (Object) this);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void corpserun$save(CompoundTag tag, CallbackInfo ci) {
        CompoundTag data = new CompoundTag();
        if (this.corpserun$corpse != null) {
            data.put("Corpse", this.corpserun$corpse.save());
        }
        data.put("Trail", this.corpserun$getTrail().save());
        data.put("EtherealBlocks", EtherealBlocks.save(this.corpserun$getEtherealBlocks()));
        if (this.corpserun$lastGround != null) {
            data.put("LastGround", this.corpserun$lastGround.save());
        }
        tag.put(CORPSERUN$TAG, data);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void corpserun$load(CompoundTag tag, CallbackInfo ci) {
        CompoundTag data = tag.getCompound(CORPSERUN$TAG);
        this.corpserun$setCorpse(data.contains("Corpse") ? CorpseLocation.load(data.getCompound("Corpse")) : null);
        this.corpserun$getTrail().copyFrom(TrailStack.load(data, "Trail"));
        EtherealBlocks.load(data, "EtherealBlocks", this.corpserun$getEtherealBlocks());
        this.corpserun$lastGround = data.contains("LastGround") ? GroundPos.load(data.getCompound("LastGround")) : null;
    }

    /**
     * Ghosts are only sent to players allowed to see them (other ghosts, unless ghostsVisibleToLiving is on), the
     * same way vanilla only sends spectators to spectators. Clients that aren't sent the ghost have no model, name
     * tag or entity-bound sounds for it. GhostManager.refreshVisibility re-checks this when ghost state changes.
     */
    @Inject(method = "broadcastToPlayer", at = @At("HEAD"), cancellable = true)
    private void corpserun$ghostOnlyForGhosts(ServerPlayer viewer, CallbackInfoReturnable<Boolean> cir) {
        if (!CorpseRun.canSee(viewer, (ServerPlayer) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    /**
     * Ghosts, and players in their post-resurrect protection window, take no damage except from sources that
     * bypass invulnerability (void, /kill).
     */
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void corpserun$ghostInvulnerable(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        boolean protectedNow = this.corpserun$protectedUntil != 0L
                && ((ServerPlayer) (Object) this).server.getTickCount() < this.corpserun$protectedUntil;
        if ((GhostManager.isGhost((ServerPlayer) (Object) this) || protectedNow) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            cir.setReturnValue(false);
        }
    }
}
