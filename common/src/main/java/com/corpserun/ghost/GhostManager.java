package com.corpserun.ghost;

import com.corpserun.Constants;
import com.corpserun.CorpseRun;
import com.corpserun.block.EtherealBlocks;
import com.corpserun.compat.SubLevels;
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
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.portal.DimensionTransition;
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
    /** The howl hint is shown at most once per this much real time (saved, so relogs and restarts don't reset it). */
    private static final long HOWL_HINT_COOLDOWN_MILLIS = 4L * 60L * 60L * 1000L;
    private static final int TRAIL_RECORD_INTERVAL = 5;

    private static final int HUM_INTERVAL = 80;
    private static final ResourceLocation SPEED_MODIFIER = Constants.id("ghost_speed");
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
        // Last stood on an airship (Sable sub-level): the body stays on it, wherever it has flown since.
        Vec3 plotPos = ground.plotPos();
        Vec3 pos = ground.pos();
        if (plotPos != null) {
            Vec3 now = SubLevels.toWorld(level, plotPos);
            if (now != null) {
                pos = now;
            }
        }
        // The spot may be far behind (an elytra flight, a long fall): make sure its chunk is loaded for the corpse.
        BlockPos corpsePos = BlockPos.containing(pos);
        level.getChunk(corpsePos);
        CorpseEntity corpse = CorpseEntity.create(level, player, pos, plotPos, SubLevels.toPlotYaw(player, player.getYRot()));
        level.addFreshEntity(corpse);
        // Freeze everywhere this life walked, ending at the body; the next life records from scratch.
        TrailStack trail = data(player).corpserun$getTrail().copy();
        trail.append(level.dimension(), corpsePos);
        data(player).corpserun$getTrail().clear();

        data(player).corpserun$setCorpse(new CorpseLocation(corpse.getUUID(), level.dimension(), pos, plotPos, trail));
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
     * (the vehicle's position is used, so the body isn't left hanging at saddle height). On a Sable sub-level the
     * spot is also kept in plot coordinates, so the corpse is laid on the airship rather than in the air where it was.
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
            data(player).corpserun$setLastGround(new GroundPos(dimension, pos, SubLevels.toPlot(standing)));
        }
    }

    /** Player copied to a new entity on respawn or End exit (loader event): carry ghost state over. */
    public static void onClone(ServerPlayer oldPlayer, ServerPlayer newPlayer) {
        data(oldPlayer).corpserun$getGuide().reset();
        data(newPlayer).corpserun$setCorpse(data(oldPlayer).corpserun$getCorpse());
        data(newPlayer).corpserun$setLastReset(data(oldPlayer).corpserun$getLastReset());
        data(newPlayer).corpserun$setLastHowlHint(data(oldPlayer).corpserun$getLastHowlHint());
        data(newPlayer).corpserun$setSeenIntro(data(oldPlayer).corpserun$hasSeenIntro());
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
            } else if (player.hasEffect(CorpseRun.INVIGORATED)) {
                // The effect is saved with the player but the protection isn't (relog or restart mid-protection):
                // drop the icon rather than show protection that no longer applies.
                player.removeEffect(CorpseRun.INVIGORATED);
            }
            recordGround(player);
            if (player.tickCount % TRAIL_RECORD_INTERVAL == 0) {
                data.corpserun$getTrail().record(player);
            }
            return;
        }

        corpse = followSubLevel(player, corpse);
        enforceGhostState(player);
        GhostCall.tick(player);
        if (!data.corpserun$hasSeenIntro()) {
            // First time ever in ghost form (saved, so once per player).
            player.sendSystemMessage(introMessage());
            data.corpserun$setSeenIntro(true);
        }

        boolean showFootprints = !CorpseRunConfig.footprintsOnlyWhenHowling() || GhostCall.isHowling(player);
        data.corpserun$getGuide().tick(player, corpse, showFootprints);
        highlightCorpse(player, corpse, GhostCall.isCorpseHighlighted(player));
        if (player.tickCount == HOWL_HINT_DELAY_TICKS) {
            long now = System.currentTimeMillis();
            long last = data.corpserun$getLastHowlHint();
            // A last time in the future (clock changed) also counts as due.
            if (last == 0L || now - last >= HOWL_HINT_COOLDOWN_MILLIS || now < last) {
                player.sendSystemMessage(howlHint());
                data.corpserun$setLastHowlHint(now);
            }
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
            } else {
                double wandered = wanderedDistance(player, corpse);
                if (wandered >= 0.0) {
                    showStatus(player, wanderedMessage(wandered));
                }
            }
        }
    }

    /**
     * A corpse on a Sable sub-level moves with it: keeps the location's world position (range checks, hum,
     * footprints) where the sub-level has carried it. While the sub-level isn't loaded, its last known spot is used.
     */
    private static CorpseLocation followSubLevel(ServerPlayer player, CorpseLocation corpse) {
        if (corpse.plotPos() == null) {
            return corpse;
        }
        ServerLevel level = player.server.getLevel(corpse.dimension());
        Vec3 now = level == null ? null : SubLevels.toWorld(level, corpse.plotPos());
        if (now == null || now.equals(corpse.pos())) {
            return corpse;
        }
        CorpseLocation moved = corpse.withPos(now);
        data(player).corpserun$setCorpse(moved);
        return moved;
    }

    /**
     * Makes the corpse glow for its ghost after a howl (GhostCall#isCorpseHighlighted, CorpseEntity#isCurrentlyGlowing).
     * Only reaches the entity while its chunk is loaded; a corpse loaded mid-glow picks it up on the next tick, and a
     * ghost who logs out mid-glow leaves it set, which shows to nobody until they're back and it's cleared.
     */
    private static void highlightCorpse(ServerPlayer player, CorpseLocation corpse, boolean highlighted) {
        ServerLevel level = player.server.getLevel(corpse.dimension());
        if (level != null && level.getEntity(corpse.corpseId()) instanceof CorpseEntity entity) {
            entity.setHighlighted(highlighted);
        }
    }

    /**
     * How far the ghost is from the nearest point of the trail back in this dimension, if that's beyond
     * wanderDistance, so the footprints can't really help any more; {@link Double#POSITIVE_INFINITY} if there is no
     * trail here at all; -1 if it hasn't wandered. Measured against the recorded trail, not the footprint route, since
     * the route is re-planned from wherever the ghost is.
     */
    private static double wanderedDistance(ServerPlayer player, CorpseLocation corpse) {
        double limit = CorpseRunConfig.wanderDistance();
        if (limit <= 0.0) {
            return -1.0;
        }
        TrailSegment segment = corpse.trail().topFor(player.level().dimension());
        if (segment == null || segment.isEmpty()) {
            return Double.POSITIVE_INFINITY;
        }
        double distSqr = segment.distSqrToClosest(player.blockPosition());
        return distSqr > limit * limit ? Math.sqrt(distSqr) : -1.0;
    }

    /**
     * Where /corpse reset sends a ghost: the earliest point still recorded on the trail its corpse holds, or its
     * spawn point, whichever is closer to where the ghost is now. One in the ghost's dimension beats one that isn't;
     * if neither is, the trail's start wins, since the footprints lead on from there. The spawn point is also the
     * fallback with no trail at all.
     */
    public static ResetTarget resetTarget(ServerPlayer player, CorpseLocation corpse) {
        // keepInventory=true here only means "don't consume a respawn anchor charge".
        DimensionTransition spawn = player.findRespawnPositionAndUseSpawnBlock(true, DimensionTransition.DO_NOTHING);
        ResetTarget spawnTarget = new ResetTarget(spawn.newLevel(), spawn.pos(), spawn.yRot(), spawn.xRot());

        TrailSegment earliest = corpse.trail().earliest();
        ServerLevel trailLevel = earliest == null ? null : player.server.getLevel(earliest.dimension());
        if (trailLevel == null) {
            return spawnTarget;
        }
        ResetTarget trailTarget = new ResetTarget(trailLevel, Vec3.atBottomCenterOf(earliest.first()), player.getYRot(), player.getXRot());
        return distanceFrom(player, trailTarget) <= distanceFrom(player, spawnTarget) ? trailTarget : spawnTarget;
    }

    private static double distanceFrom(ServerPlayer player, ResetTarget target) {
        return target.level() == player.level() ? target.pos().distanceTo(player.position()) : Double.MAX_VALUE;
    }

    public record ResetTarget(ServerLevel level, Vec3 pos, float yRot, float xRot) {}

    /** Keeps the visible effect and the no-build ability in place (milk, /effect clear, gamemode changes...). */
    private static void enforceGhostState(ServerPlayer player) {
        if (!player.hasEffect(CorpseRun.GHOST)) {
            player.addEffect(new MobEffectInstance(CorpseRun.GHOST, MobEffectInstance.INFINITE_DURATION, 0, true, false, true));
        }
        if (player.getAbilities().mayBuild) {
            player.getAbilities().mayBuild = false;
            player.onUpdateAbilities();
        }
        enforceGhostFlight(player);

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

    /**
     * Creative-style flight for ghosts while ghostFlight is on. Only flight the mod granted is ever taken away (when
     * the setting was turned off since), so flight from creative, spectator or other mods is left alone.
     */
    private static void enforceGhostFlight(ServerPlayer player) {
        GhostData data = data(player);
        Abilities abilities = player.getAbilities();
        if (CorpseRunConfig.ghostFlight()) {
            if (!abilities.mayfly) {
                abilities.mayfly = true;
                data.corpserun$setGrantedFlight(true);
                player.onUpdateAbilities();
            }
        } else if (data.corpserun$hasGrantedFlight()) {
            data.corpserun$setGrantedFlight(false);
            if (!player.gameMode.getGameModeForPlayer().isCreative() && !player.isSpectator()) {
                abilities.mayfly = false;
                abilities.flying = false;
                player.onUpdateAbilities();
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
        resurrect(player, true, true);
    }

    /**
     * Gives up on the corpse ({@code /resurrect accept} away from it): resurrects the ghost where they stand, but
     * without the Invigorated protection a real corpse run earns, and without the advancement.
     */
    public static void resurrectInPlace(ServerPlayer player) {
        resurrect(player, false, false);
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
        resurrect(player, false, true);
        return true;
    }

    private static void resurrect(ServerPlayer player, boolean awardAdvancement, boolean protect) {
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

        // Also takes away ghost flight (unless the game mode allows flying).
        player.gameMode.getGameModeForPlayer().updatePlayerAbilities(player.getAbilities());
        player.onUpdateAbilities();
        data.corpserun$setGrantedFlight(false);

        data.corpserun$setResurrectPromptUntil(0L);
        if (protect) {
            startProtection(player);
        }
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
     * A few seconds of invulnerability, with extra knockback on the player's hits and whatever they hit slowed, to
     * escape whatever killed them.
     */
    private static void startProtection(ServerPlayer player) {
        int ticks = (int) Math.round(CorpseRunConfig.resurrectProtectionSeconds() * 20.0);
        if (ticks <= 0) {
            return;
        }
        data(player).corpserun$setProtectedUntil(player.server.getTickCount() + ticks);
        showInvigorated(player, ticks);
    }

    /**
     * Knockback added to the player's hits while Invigorated (LivingEntityMixin, on LivingEntity#getKnockback), or 0.
     * Hit knockback is BASE_HIT_KNOCKBACK plus half of getKnockback, so adding (multiplier - 1) * BASE / 0.5 makes a
     * normal hit exactly the configured percentage (sprint hits a bit less). Added there rather than as an
     * ATTACK_KNOCKBACK modifier, since that attribute is capped at 5 (about 725%).
     */
    public static float invigoratedKnockbackBonus(ServerPlayer player) {
        if (!isInvigorated(player)) {
            return 0.0F;
        }
        double extra = (CorpseRunConfig.resurrectKnockbackPercent() / 100.0 - 1.0) * BASE_HIT_KNOCKBACK / 0.5;
        return (float) Math.max(0.0, extra);
    }

    /** Slowness III: 15% per level, so 45% slower. */
    private static final int HIT_SLOWNESS_AMPLIFIER = 2;

    /**
     * An Invigorated player's melee hit landed on {@code target} (LivingEntityMixin): vanilla Slowness III (45% slower)
     * for {@code resurrectHitSlowSeconds}. Each hit starts that over; a stronger or longer Slowness already on the
     * target is kept, as vanilla does.
     */
    public static void onInvigoratedHit(ServerPlayer player, LivingEntity target) {
        int ticks = (int) Math.round(CorpseRunConfig.resurrectHitSlowSeconds() * 20.0);
        if (ticks > 0 && isInvigorated(player)) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, HIT_SLOWNESS_AMPLIFIER), player);
        }
    }

    /** In the post-resurrect protection window. */
    private static boolean isInvigorated(ServerPlayer player) {
        long protectedUntil = data(player).corpserun$getProtectedUntil();
        return protectedUntil != 0L && player.server.getTickCount() < protectedUntil;
    }

    /** The "Invigorated" icon, counting down the protection. No particles, so it's just the HUD/inventory icon. */
    private static void showInvigorated(ServerPlayer player, int ticks) {
        player.addEffect(new MobEffectInstance(CorpseRun.INVIGORATED, ticks, 0, false, false, true));
    }

    private static void endProtection(ServerPlayer player) {
        data(player).corpserun$setProtectedUntil(0L);
        player.removeEffect(CorpseRun.INVIGORATED);
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

    private static Component introMessage() {
        Component command = Component.literal("/resurrect").withStyle(ChatFormatting.GREEN);
        return Component.translatableWithFallback("corpserun.message.intro",
                "You are now spirit walking, the spirit realm is safe and will guide you to your corpse. Use %s when you're ready.",
                command).withStyle(ChatFormatting.GRAY);
    }

    /** The chat hint; the key is shown as whatever the player has attack bound to (left click by default). */
    private static Component howlHint() {
        Component key = Component.keybind("key.attack").withStyle(ChatFormatting.GREEN);
        return Component.translatableWithFallback("corpserun.message.howl_hint",
                "Lost spirits may howl to find their way home.. Press %s to howl.", key).withStyle(ChatFormatting.GRAY);
    }

    /** @param distance blocks from the trail back, or infinite if there's none in this dimension */
    private static Component wanderedMessage(double distance) {
        Component command = Component.literal("/corpse reset").withStyle(ChatFormatting.GREEN);
        if (Double.isInfinite(distance)) {
            return Component.translatableWithFallback("corpserun.message.wandered_elsewhere",
                    "Your spirit has wandered quite far, your path doesn't pass through here.. %s?", command);
        }
        Component blocks = Component.literal(Long.toString(Math.round(distance))).withStyle(ChatFormatting.YELLOW);
        return Component.translatableWithFallback("corpserun.message.wandered",
                "Your spirit has wandered quite far, %s blocks from your path.. %s?", blocks, command);
    }
}
