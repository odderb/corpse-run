package com.corpserun.ghost;

import com.corpserun.Constants;
import com.corpserun.CorpseRun;
import com.corpserun.block.EtherealBlocks;
import com.corpserun.config.CorpseRunConfig;
import com.corpserun.entity.CorpseEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Server-side ghost lifecycle: death, respawn, per-tick upkeep and resurrection. */
public final class GhostManager {

    private static final int RANGE_CHECK_INTERVAL = 5;
    /** Status messages fade after 3 seconds; re-sending the in-range one this often keeps it up while in range. */
    private static final int IN_RANGE_REFRESH_INTERVAL = 40;
    /**
     * When to tell a ghost about howling: 15 seconds in. Counted from when this player entity appeared (respawn as
     * a ghost, or logging back in as one), so a ghost who relogs gets the reminder again.
     */
    private static final int HOWL_HINT_DELAY_TICKS = 15 * 20;
    private static final int TRAIL_RECORD_INTERVAL = 5;

    private static final int HUM_INTERVAL = 80;
    private static final ResourceLocation SPEED_MODIFIER = Constants.id("ghost_speed");
    private static final ResourceLocation KNOCKBACK_MODIFIER = Constants.id("resurrect_knockback");
    /** Granted by code on the first resurrect; its only criterion is minecraft:impossible. */
    private static final ResourceLocation CORPSE_RUN_ADVANCEMENT = Constants.id("corpse_run");
    /** Knockback every melee hit gets regardless of the attribute (LivingEntity#hurt). */
    private static final double BASE_HIT_KNOCKBACK = 0.4;
    /** Real beacons use BLOCKS, so stopping beacon-ambient in AMBIENT only ever cuts off our hum. */
    private static final SoundSource HUM_SOURCE = SoundSource.AMBIENT;

    private GhostManager() {
    }

    private static GhostData data(ServerPlayer player) {
        return (GhostData) player;
    }

    /**
     * True if the entity is a player currently in ghost form. The one ghost check for the whole mod, on both logical
     * sides: it reads the synced flag, which the server sets together with the corpse location.
     */
    public static boolean isGhost(@Nullable Entity entity) {
        return entity instanceof GhostPlayer ghost && ghost.corpserun$isGhost();
    }

    /** Player death (ServerPlayerMixin, end of die()). Lays down a corpse unless the player was already a ghost. */
    public static void onDeath(ServerPlayer player) {
        if (isGhost(player)) {
            // Died again while a ghost (void, /kill): the original corpse stays where it is.
            return;
        }

        GroundPos ground = corpseSpot(player);
        data(player).corpserun$setLastGround(null);
        ServerLevel level = player.server.getLevel(ground.dimension());
        if (level == null) {
            level = player.serverLevel();
            ground = new GroundPos(level.dimension(), player.position());
        }
        // The spot may be far behind (an elytra flight, a long fall): make sure its chunk is loaded for the corpse.
        BlockPos corpsePos = BlockPos.containing(ground.pos());
        level.getChunk(corpsePos);
        CorpseEntity corpse = CorpseEntity.create(level, player, ground.pos());
        level.addFreshEntity(corpse);
        // Freeze everywhere this life walked, ending at the body; the next life records from scratch.
        TrailStack trail = data(player).corpserun$getTrail().copy();
        trail.append(level.dimension(), corpsePos);
        data(player).corpserun$getTrail().clear();

        data(player).corpserun$setCorpse(new CorpseLocation(corpse.getUUID(), level.dimension(), ground.pos(), trail));
        data(player).corpserun$setInRange(false);
    }

    /**
     * Where the corpse goes: the last place this life stood on the ground, so deaths in the void, in mid-air or
     * while flying leave a body that can be reached. Only if the player never touched the ground (e.g. died right
     * after joining in mid-air) is it where they died, kept inside the world's height range.
     */
    private static GroundPos corpseSpot(ServerPlayer player) {
        GroundPos last = data(player).corpserun$getLastGround();
        if (last != null) {
            return last;
        }
        ServerLevel level = player.serverLevel();
        Vec3 pos = player.position();
        double y = Math.max(pos.y, level.getMinBuildHeight());
        return new GroundPos(level.dimension(), new Vec3(pos.x, y, pos.z));
    }

    /**
     * Remembers where a living player last stood on the ground. Riding counts when the vehicle is on the ground
     * (the vehicle's position is used, so the body isn't left hanging at saddle height).
     */
    private static void recordGround(ServerPlayer player) {
        if (player.isSpectator()) {
            return;
        }
        Entity standing = player.getRootVehicle();
        if (!standing.onGround()) {
            return;
        }
        GroundPos last = data(player).corpserun$getLastGround();
        Vec3 pos = standing.position();
        ResourceKey<Level> dimension = player.level().dimension();
        if (last == null || !last.pos().equals(pos) || !last.dimension().equals(dimension)) {
            data(player).corpserun$setLastGround(new GroundPos(dimension, pos));
        }
    }

    /** Player copied to a new entity on respawn or End exit (loader event): carry ghost state over. */
    public static void onClone(ServerPlayer oldPlayer, ServerPlayer newPlayer) {
        data(oldPlayer).corpserun$getGuide().reset();
        data(newPlayer).corpserun$setCorpse(data(oldPlayer).corpserun$getCorpse());
        data(newPlayer).corpserun$setLastReset(data(oldPlayer).corpserun$getLastReset());
        data(newPlayer).corpserun$getTrail().copyFrom(data(oldPlayer).corpserun$getTrail());
        data(newPlayer).corpserun$getEtherealBlocks().addAll(data(oldPlayer).corpserun$getEtherealBlocks());
        // Only still set when the player wasn't killed (End exit); onDeath clears it once the corpse is placed.
        data(newPlayer).corpserun$setLastGround(data(oldPlayer).corpserun$getLastGround());
    }

    public static void tick(ServerPlayer player) {
        GhostData data = data(player);
        if (!player.isAlive()) {
            return;
        }
        CorpseLocation corpse = data.corpserun$getCorpse();
        if (corpse == null) {
            long protectedUntil = data.corpserun$getProtectedUntil();
            if (protectedUntil != 0L) {
                long remaining = protectedUntil - player.server.getTickCount();
                if (remaining <= 0) {
                    endProtection(player);
                } else if (!player.hasEffect(CorpseRun.INVIGORATED)) {
                    // Milk or /effect clear removed the icon; protection still runs, so show it again.
                    showInvigorated(player, (int) remaining);
                }
            }
            recordGround(player);
            if (player.tickCount % TRAIL_RECORD_INTERVAL == 0) {
                data.corpserun$getTrail().record(player);
            }
            return;
        }

        enforceGhostState(player);
        GhostCall.tick(player);

        data.corpserun$getGuide().tick(player, corpse, GhostCall.isHowling(player));
        if (player.tickCount == HOWL_HINT_DELAY_TICKS) {
            player.sendSystemMessage(howlHint());
        }

        boolean sameDimension = player.level().dimension().equals(corpse.dimension());
        if (sameDimension && CorpseRunConfig.corpseHum() && player.tickCount % HUM_INTERVAL == 0) {
            playHum(player, corpse.pos());
        }

        if (player.tickCount % RANGE_CHECK_INTERVAL != 0) {
            return;
        }

        boolean inRange = isInRange(player, corpse);
        if (inRange != data.corpserun$isInRange()) {
            data.corpserun$setInRange(inRange);
            // Leaving range needs no message: the in-range one stops being refreshed and fades out.
            if (inRange) {
                showStatus(player, inRangeMessage());
            }
        } else if (player.tickCount % IN_RANGE_REFRESH_INTERVAL == 0) {
            if (inRange) {
                showStatus(player, inRangeMessage());
            } else if (hasWandered(player, corpse)) {
                showStatus(player, wanderedMessage());
            }
        }
    }

    /**
     * Too far from every point of the trail back in this dimension (or there is none here), so the footprints can't
     * really help any more. Measured against the recorded trail, not the footprint route, since the route is
     * re-planned from wherever the ghost is.
     */
    private static boolean hasWandered(ServerPlayer player, CorpseLocation corpse) {
        double limit = CorpseRunConfig.wanderDistance();
        if (limit <= 0.0) {
            return false;
        }
        TrailSegment segment = corpse.trail().topFor(player.level().dimension());
        return segment == null || segment.isEmpty() || segment.distSqrToClosest(player.blockPosition()) > limit * limit;
    }

    /** Keeps the visible effect and the no-build ability in place (milk, /effect clear, gamemode changes...). */
    private static void enforceGhostState(ServerPlayer player) {
        if (!player.hasEffect(CorpseRun.GHOST)) {
            player.addEffect(new MobEffectInstance(CorpseRun.GHOST, MobEffectInstance.INFINITE_DURATION, 0, true, false, true));
        }
        if (player.getAbilities().mayBuild) {
            player.getAbilities().mayBuild = false;
            player.onUpdateAbilities();
        }

        // Transient (not saved), so it's simply reapplied here after respawn/relog and can't outlive ghost form.
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        double bonus = CorpseRunConfig.ghostSpeedPercent() / 100.0 - 1.0;
        if (speed != null) {
            AttributeModifier current = speed.getModifier(SPEED_MODIFIER);
            if (bonus == 0.0) {
                if (current != null) {
                    speed.removeModifier(SPEED_MODIFIER);
                }
            } else if (current == null || current.amount() != bonus) {
                speed.addOrUpdateTransientModifier(
                        new AttributeModifier(SPEED_MODIFIER, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
    }

    /** A low beacon hum from the corpse, heard only by its ghost; stereo gives a rough direction. */
    private static void playHum(ServerPlayer player, Vec3 corpsePos) {
        double range = CorpseRunConfig.humRange();
        if (range <= 0.0 || player.position().distanceToSqr(corpsePos) > range * range) {
            return;
        }
        // Minecraft sounds are audible out to 16 blocks per unit of volume.
        float volume = (float) Math.max(1.0, range / 16.0);
        player.connection.send(new ClientboundSoundPacket(
                BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.BEACON_AMBIENT), HUM_SOURCE,
                corpsePos.x, corpsePos.y + 0.5, corpsePos.z, volume, 0.6F, player.getRandom().nextLong()));
    }

    /** The tag of a fluid ghosts can walk on (water or lava), or null for any other fluid. */
    @Nullable
    public static TagKey<Fluid> walkableFluid(FluidState fluid) {
        if (fluid.is(FluidTags.WATER)) {
            return FluidTags.WATER;
        }
        if (fluid.is(FluidTags.LAVA)) {
            return FluidTags.LAVA;
        }
        return null;
    }

    public static boolean isInRange(ServerPlayer player, CorpseLocation corpse) {
        if (!player.level().dimension().equals(corpse.dimension())) {
            return false;
        }
        double range = CorpseRunConfig.resurrectRange();
        return player.position().distanceToSqr(corpse.pos()) <= range * range;
    }

    /** Ends ghost form: removes the corpse (if loaded) and the effect, and restores normal abilities. */
    public static void resurrect(ServerPlayer player) {
        resurrect(player, true);
    }

    /**
     * Admin fix ({@code /corpse fix}): resurrects a ghost where they stand, whatever the distance to the corpse.
     * Same as a normal resurrect, except it doesn't count toward the Corpse Run advancement.
     *
     * @return false if the player isn't a ghost
     */
    public static boolean forceResurrect(ServerPlayer player) {
        if (!isGhost(player)) {
            return false;
        }
        resurrect(player, false);
        return true;
    }

    private static void resurrect(ServerPlayer player, boolean awardAdvancement) {
        GhostData data = data(player);
        CorpseLocation corpse = data.corpserun$getCorpse();
        if (corpse == null) {
            return;
        }

        ServerLevel corpseLevel = player.server.getLevel(corpse.dimension());
        if (corpseLevel != null) {
            Entity entity = corpseLevel.getEntity(corpse.corpseId());
            if (entity instanceof CorpseEntity) {
                entity.discard();
            }
            // If the corpse's chunk isn't loaded it removes itself once it loads (see CorpseEntity#tick).
        }

        data.corpserun$setCorpse(null);
        data.corpserun$setInRange(false);
        // Clear the in-range status line instead of letting it linger after resurrecting.
        showStatus(player, Component.empty());
        refreshVisibility(player);
        // The nether portal "arrival" whoosh, played in the world so players nearby hear the revive too.
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.335F, 1.0F);
        data.corpserun$getGuide().reset();
        EtherealBlocks.removeAll(player);
        player.removeEffect(CorpseRun.GHOST);
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SPEED_MODIFIER);
        }
        // Cut off a hum that's still playing instead of letting it ring out.
        player.connection.send(new ClientboundStopSoundPacket(SoundEvents.BEACON_AMBIENT.getLocation(), HUM_SOURCE));

        player.gameMode.getGameModeForPlayer().updatePlayerAbilities(player.getAbilities());
        player.onUpdateAbilities();

        startProtection(player);
        if (awardAdvancement) {
            awardCorpseRun(player);
        }
    }

    private static void awardCorpseRun(ServerPlayer player) {
        AdvancementHolder advancement = player.server.getAdvancements().get(CORPSE_RUN_ADVANCEMENT);
        if (advancement != null) {
            player.getAdvancements().award(advancement, "resurrected");
        }
    }

    /**
     * A few seconds of invulnerability with extra knockback on the player's hits, to escape whatever killed them.
     * Player hit knockback is BASE_HIT_KNOCKBACK plus half the ATTACK_KNOCKBACK attribute, so adding
     * (multiplier - 1) * BASE / 0.5 makes a normal hit exactly the configured percentage (sprint hits a bit less).
     */
    private static void startProtection(ServerPlayer player) {
        int ticks = (int) Math.round(CorpseRunConfig.resurrectProtectionSeconds() * 20.0);
        if (ticks <= 0) {
            return;
        }
        data(player).corpserun$setProtectedUntil(player.server.getTickCount() + ticks);
        showInvigorated(player, ticks);

        double extra = (CorpseRunConfig.resurrectKnockbackPercent() / 100.0 - 1.0) * BASE_HIT_KNOCKBACK / 0.5;
        AttributeInstance knockback = player.getAttribute(Attributes.ATTACK_KNOCKBACK);
        if (knockback != null && extra > 0.0) {
            knockback.addOrUpdateTransientModifier(new AttributeModifier(KNOCKBACK_MODIFIER, extra, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    /** The "Invigorated" icon, counting down the protection. No particles, so it's just the HUD/inventory icon. */
    private static void showInvigorated(ServerPlayer player, int ticks) {
        player.addEffect(new MobEffectInstance(CorpseRun.INVIGORATED, ticks, 0, false, false, true));
    }

    private static void endProtection(ServerPlayer player) {
        data(player).corpserun$setProtectedUntil(0L);
        player.removeEffect(CorpseRun.INVIGORATED);
        AttributeInstance knockback = player.getAttribute(Attributes.ATTACK_KNOCKBACK);
        if (knockback != null) {
            knockback.removeModifier(KNOCKBACK_MODIFIER);
        }
    }

    /**
     * Re-sends or removes this player for everyone (and everyone for this player) after their ghost state changed,
     * since who can see whom depends on it (see ServerPlayerMixin.broadcastToPlayer). New ghosts don't need this:
     * a respawned player is a new entity whose visibility is checked when it's added.
     */
    public static void refreshVisibility(ServerPlayer player) {
        player.serverLevel().getChunkSource().move(player);
    }

    /** The status line above the hotbar (the "action bar"), instead of chat. */
    public static void showStatus(ServerPlayer player, Component message) {
        player.displayClientMessage(message, true);
    }

    private static Component inRangeMessage() {
        // Status messages can't be clicked, so the command is just highlighted.
        Component command = Component.literal("/resurrect").withStyle(ChatFormatting.GREEN);
        return Component.translatableWithFallback("corpserun.message.in_range", "In range of corpse, %s?", command);
    }

    /** The chat hint; the key is shown as whatever the player has attack bound to (left click by default). */
    private static Component howlHint() {
        Component key = Component.keybind("key.attack").withStyle(ChatFormatting.GREEN);
        return Component.translatableWithFallback("corpserun.message.howl_hint",
                "Lost spirits may howl to find their way home.. Press %s to howl.", key).withStyle(ChatFormatting.GRAY);
    }

    private static Component wanderedMessage() {
        Component command = Component.literal("/corpse reset").withStyle(ChatFormatting.GREEN);
        return Component.translatableWithFallback("corpserun.message.wandered", "Your spirit has wandered quite far.. %s?", command);
    }
}
