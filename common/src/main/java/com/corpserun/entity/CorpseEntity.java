package com.corpserun.entity;

import com.corpserun.CorpseRun;
import com.corpserun.ghost.CorpseLocation;
import com.corpserun.ghost.GhostData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Purely cosmetic body left where a player last stood on the ground before dying. It holds no items: loot is left
 * entirely to vanilla / other mods. It never moves, so it always lies exactly at the point the ghost has to reach
 * (the CorpseLocation the server checks range against).
 */
public class CorpseEntity extends Entity {

    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER =
            SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> DATA_OWNER_NAME =
            SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.STRING);

    public CorpseEntity(EntityType<? extends CorpseEntity> type, Level level) {
        super(type, level);
    }

    public static CorpseEntity create(Level level, Player owner, Vec3 pos) {
        CorpseEntity corpse = new CorpseEntity(CorpseRun.CORPSE.get(), level);
        corpse.moveTo(pos.x, pos.y, pos.z, owner.getYRot(), 0.0F);
        corpse.setOwner(owner.getUUID(), owner.getGameProfile().getName());
        return corpse;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_OWNER, Optional.empty());
        builder.define(DATA_OWNER_NAME, "");
    }

    public void setOwner(UUID uuid, String name) {
        this.entityData.set(DATA_OWNER, Optional.of(uuid));
        this.entityData.set(DATA_OWNER_NAME, name);
    }

    @Nullable
    public UUID getOwnerId() {
        return this.entityData.get(DATA_OWNER).orElse(null);
    }

    public String getOwnerName() {
        return this.entityData.get(DATA_OWNER_NAME);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && this.tickCount % 20 == 0) {
            removeIfStale();
        }
    }

    /**
     * Discard this corpse if its owner is online and no longer a ghost tied to it (e.g. they resurrected while this
     * corpse's chunk was unloaded). Offline owners keep their corpse.
     */
    private void removeIfStale() {
        UUID owner = this.getOwnerId();
        if (owner == null || this.getServer() == null) {
            this.discard();
            return;
        }
        ServerPlayer player = this.getServer().getPlayerList().getPlayer(owner);
        if (player == null) {
            return;
        }
        CorpseLocation corpse = ((GhostData) player).corpserun$getCorpse();
        if (corpse == null || !corpse.corpseId().equals(this.getUUID())) {
            this.discard();
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean canChangeDimensions(Level oldLevel, Level newLevel) {
        return false;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        // The body is rendered lying down and is longer than its hitbox.
        return this.getBoundingBox().inflate(1.0, 0.5, 1.0);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) {
            this.setOwner(tag.getUUID("Owner"), tag.getString("OwnerName"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        UUID owner = this.getOwnerId();
        if (owner != null) {
            tag.putUUID("Owner", owner);
            tag.putString("OwnerName", this.getOwnerName());
        }
    }
}
