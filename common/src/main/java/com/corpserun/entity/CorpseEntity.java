package com.corpserun.entity;

import com.corpserun.CorpseRun;
import com.corpserun.compat.SubLevels;
import com.corpserun.ghost.CorpseLocation;
import com.corpserun.ghost.GhostData;
import com.corpserun.ghost.PlotPos;
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
 * entirely to vanilla / other mods. It never moves on its own, so it always lies exactly at the point the ghost has
 * to reach (the CorpseLocation the server checks range against). A corpse on a Sable sub-level (an airship) is
 * carried along with it each tick, from its plot position.
 * <p>
 * After its ghost howls, the corpse glows for a while (the outline seen through walls), for that ghost only.
 */
public class CorpseEntity extends Entity {

    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER =
            SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> DATA_OWNER_NAME =
            SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.STRING);
    /** Set by the server for a while after the owner howls. Synced to everyone nearby, but only the owner's client acts on it. */
    private static final EntityDataAccessor<Boolean> DATA_HIGHLIGHTED =
            SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.BOOLEAN);
    /** Outline color while highlighted: the pale ghostly blue of the footprints. */
    private static final int HIGHLIGHT_COLOR = 0xE0F5FF;

    /**
     * The local player on the client (updated every client tick by CorpseHighlight), or null. Kept here so this
     * common class never touches client-only classes.
     */
    @Nullable
    public static UUID localPlayer;

    /** Position on the sub-level this corpse lies on, in plot coordinates; null on normal ground. Server only. */
    @Nullable
    private Vec3 plotPos;
    /** Facing relative to that sub-level. */
    private float plotYaw;

    public CorpseEntity(EntityType<? extends CorpseEntity> type, Level level) {
        super(type, level);
    }

    /**
     * @param plotPos where the body lies on a sub-level, in plot coordinates, or null; {@code pos} is then where
     *                that currently is in the world
     */
    public static CorpseEntity create(Level level, Player owner, Vec3 pos, @Nullable Vec3 plotPos, float plotYaw) {
        CorpseEntity corpse = new CorpseEntity(CorpseRun.CORPSE.get(), level);
        float yaw = plotPos == null ? owner.getYRot() : SubLevels.toWorldYaw(level, plotPos, plotYaw);
        corpse.moveTo(pos.x, pos.y, pos.z, yaw, 0.0F);
        corpse.setOwner(owner.getUUID(), owner.getGameProfile().getName());
        corpse.plotPos = plotPos;
        corpse.plotYaw = plotYaw;
        return corpse;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_OWNER, Optional.empty());
        builder.define(DATA_OWNER_NAME, "");
        builder.define(DATA_HIGHLIGHTED, false);
    }

    /** Server: whether the owner howled recently enough for the glow (see GhostManager). */
    public void setHighlighted(boolean highlighted) {
        this.entityData.set(DATA_HIGHLIGHTED, highlighted);
    }

    /**
     * The glowing outline, drawn through walls, but only on the owner's client: everyone else, ghosts included, never
     * sees another player's corpse highlighted.
     */
    @Override
    public boolean isCurrentlyGlowing() {
        if (this.level().isClientSide && this.entityData.get(DATA_HIGHLIGHTED)) {
            UUID owner = this.getOwnerId();
            return owner != null && owner.equals(localPlayer);
        }
        return super.isCurrentlyGlowing();
    }

    @Override
    public int getTeamColor() {
        return this.entityData.get(DATA_HIGHLIGHTED) ? HIGHLIGHT_COLOR : super.getTeamColor();
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
        if (this.level().isClientSide) {
            return;
        }
        followSubLevel();
        if (this.tickCount % 20 == 0) {
            removeIfStale();
        }
    }

    /** Rides along with the sub-level it lies on. Stays put while that isn't loaded (or once it's gone). */
    private void followSubLevel() {
        if (this.plotPos == null) {
            return;
        }
        Vec3 world = SubLevels.toWorld(this.level(), this.plotPos);
        if (world == null) {
            return;
        }
        float yaw = SubLevels.toWorldYaw(this.level(), this.plotPos, this.plotYaw);
        if (!world.equals(this.position()) || yaw != this.getYRot()) {
            this.moveTo(world.x, world.y, world.z, yaw, 0.0F);
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
        this.plotPos = PlotPos.load(tag);
        this.plotYaw = tag.getFloat("PlotYaw");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        UUID owner = this.getOwnerId();
        if (owner != null) {
            tag.putUUID("Owner", owner);
            tag.putString("OwnerName", this.getOwnerName());
        }
        PlotPos.save(tag, this.plotPos);
        if (this.plotPos != null) {
            tag.putFloat("PlotYaw", this.plotYaw);
        }
    }
}
