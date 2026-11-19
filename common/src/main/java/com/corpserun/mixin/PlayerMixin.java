package com.corpserun.mixin;

import com.corpserun.ghost.GhostPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Synced ghost flag, plus the ghost restrictions that apply on both logical sides. */
@Mixin(Player.class)
public abstract class PlayerMixin extends LivingEntity implements GhostPlayer {

    @Unique
    private static final EntityDataAccessor<Boolean> CORPSERUN$DATA_GHOST =
            SynchedEntityData.defineId(Player.class, EntityDataSerializers.BOOLEAN);

    protected PlayerMixin(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
    }

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void corpserun$defineGhostData(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(CORPSERUN$DATA_GHOST, false);
    }

    @Override
    public boolean corpserun$isGhost() {
        return this.entityData.get(CORPSERUN$DATA_GHOST);
    }

    @Override
    public void corpserun$setGhost(boolean ghost) {
        this.entityData.set(CORPSERUN$DATA_GHOST, ghost);
    }

    /** Mobs' targeting conditions consult this: ghosts are never seen as enemies. */
    @Inject(method = "canBeSeenAsEnemy", at = @At("HEAD"), cancellable = true)
    private void corpserun$ghostNotEnemy(CallbackInfoReturnable<Boolean> cir) {
        if (this.corpserun$isGhost()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void corpserun$ghostCannotAttack(Entity target, CallbackInfo ci) {
        if (this.corpserun$isGhost()) {
            ci.cancel();
        }
    }

    /** Checked by both client and server before breaking a block. */
    @Inject(method = "blockActionRestricted", at = @At("HEAD"), cancellable = true)
    private void corpserun$ghostCannotBreak(Level level, BlockPos pos, GameType gameMode, CallbackInfoReturnable<Boolean> cir) {
        if (this.corpserun$isGhost()) {
            cir.setReturnValue(true);
        }
    }

    /** No trading, riding, item frames, chest minecarts, etc. */
    @Inject(method = "interactOn", at = @At("HEAD"), cancellable = true)
    private void corpserun$ghostCannotInteract(Entity target, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (this.corpserun$isGhost()) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
