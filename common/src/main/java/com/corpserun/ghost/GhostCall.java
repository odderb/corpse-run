package com.corpserun.ghost;

import com.corpserun.Constants;
import com.corpserun.CorpseRun;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.io.InputStream;
import java.util.List;
import java.util.function.Supplier;

/**
 * A ghost's left click: sings one of the call clips ({@code corpserun:ghost.call1} to {@code ghost.call4}, picked at
 * random here on the server so everyone hears the same one), on the ghost, so it follows the ghost as it moves.
 * <p>
 * While the clip plays, notes ({@code corpserun:singing}) float out of the ghost's mouth, sent to everyone in range.
 * The next call is allowed after {@link #COOLDOWN_FRACTION} of the clip. Clip lengths are read from the .ogg files in
 * the mod jar (they're there on dedicated servers too), so re-exported sounds need no code change.
 */
public final class GhostCall {

    /**
     * How many call clips there are. Clip {@code i} (from 0) is the sound {@code corpserun:ghost.call<i+1>} (see
     * sounds.json and {@link CorpseRun#GHOST_CALL_SOUNDS}), played from {@code sounds/ghost/call<i+1>.ogg}.
     */
    public static final int CLIP_COUNT = 4;
    /** The next call is allowed once this much of the current clip has played. */
    private static final double COOLDOWN_FRACTION = 0.75;
    /** Used when a clip's length can't be read. */
    private static final int FALLBACK_CLIP_TICKS = 40;
    /**
     * The client only sends a call request this often, to keep held or spammed clicks from flooding the server; the
     * real cooldown is decided here on the server.
     */
    public static final int CLIENT_THROTTLE_TICKS = 10;

    /** One note every this many ticks while singing. */
    private static final int NOTE_INTERVAL = 8;
    /**
     * Where the ghost model's mouth is: height above the feet and distance in front of the body's center, in blocks.
     * Tune these to the model.
     */
    private static final double MOUTH_HEIGHT = 0.8;
    private static final double MOUTH_FORWARD = 0.35;
    /** Notes leave the mouth at this speed (blocks per tick) going forward, and this speed going up. */
    private static final double NOTE_SPEED = 0.03;
    private static final double NOTE_RISE = 0.02;

    /** Clip lengths in ticks, read on first use. */
    private static int[] clipTicks;

    /**
     * A howl: when it started (server tick) and how long its clip is. Stored on the player (GhostData), so it never
     * outlives the player entity: joining, leaving, respawning or switching worlds all start with no song.
     */
    public record Song(int startTick, int lengthTicks) {

        /** Ticks since the song started; negative only if the clock went backwards, which counts as not playing. */
        int elapsed(int now) {
            return now - this.startTick;
        }

        boolean isPlaying(int now) {
            int elapsed = elapsed(now);
            return elapsed >= 0 && elapsed < this.lengthTicks;
        }
    }

    private GhostCall() {
    }

    /** Handles {@link GhostCallPayload} on the server thread. */
    public static void handle(ServerPlayer player) {
        if (!GhostManager.isGhost(player)) {
            return;
        }
        GhostData data = (GhostData) player;
        int now = player.server.getTickCount();
        Song last = data.corpserun$getSong();
        if (last != null) {
            int elapsed = last.elapsed(now);
            if (elapsed >= 0 && elapsed < Math.round(last.lengthTicks() * COOLDOWN_FRACTION)) {
                return;
            }
        }

        List<Supplier<SoundEvent>> sounds = CorpseRun.GHOST_CALL_SOUNDS;
        int clip = player.getRandom().nextInt(sounds.size());
        data.corpserun$setSong(new Song(now, clipTicks()[clip]));

        float pitch = 0.9F + player.getRandom().nextFloat() * 0.2F;
        SoundEvent sound = sounds.get(clip).get();
        // Bound to the ghost (entity sound packet), so it follows them as they move. Mono files only: stereo sounds
        // aren't positioned at all.
        player.level().playSound(null, player, sound, SoundSource.PLAYERS, 1.0F, pitch);
        // Players the ghost is hidden from don't know the entity, so they'd drop that packet. They get the sound at
        // the ghost's position instead: they hear it, it just stays where it started.
        Holder<SoundEvent> holder = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound);
        double range = sound.getRange(1.0F);
        long seed = player.getRandom().nextLong();
        for (ServerPlayer listener : player.serverLevel().players()) {
            if (!CorpseRun.canSee(listener, player) && listener.distanceToSqr(player) <= range * range) {
                listener.connection.send(new ClientboundSoundPacket(holder, SoundSource.PLAYERS,
                        player.getX(), player.getEyeY(), player.getZ(), 1.0F, pitch, seed));
            }
        }
    }

    /** True while the ghost's current howl is playing (footprints are only shown then). */
    public static boolean isHowling(ServerPlayer player) {
        Song song = ((GhostData) player).corpserun$getSong();
        return song != null && song.isPlaying(player.server.getTickCount());
    }

    /** Called every tick for ghosts: emits the next note while the song plays. */
    public static void tick(ServerPlayer player) {
        Song song = ((GhostData) player).corpserun$getSong();
        int now = player.server.getTickCount();
        if (song == null || !song.isPlaying(now) || song.elapsed(now) % NOTE_INTERVAL != 0) {
            return;
        }
        // The ghost model faces the body's direction, not the head's.
        Vec3 forward = Vec3.directionFromRotation(0.0F, player.yBodyRot);
        Vec3 mouth = player.position().add(forward.scale(MOUTH_FORWARD)).add(0.0, MOUTH_HEIGHT, 0.0);
        RandomSource random = player.getRandom();
        Vec3 velocity = forward.scale(NOTE_SPEED).add(
                (random.nextDouble() - 0.5) * 0.01, NOTE_RISE + random.nextDouble() * 0.01, (random.nextDouble() - 0.5) * 0.01);
        // Count 0: the offset is the note's velocity. Sent to each nearby player who can see this ghost.
        for (ServerPlayer viewer : player.serverLevel().players()) {
            if (CorpseRun.canSee(viewer, player)) {
                player.serverLevel().sendParticles(viewer, CorpseRun.SINGING.get(), false, mouth.x, mouth.y, mouth.z, 0,
                        velocity.x, velocity.y, velocity.z, 1.0);
            }
        }
    }

    /** Sound event name of a clip (0-based), e.g. {@code ghost.call1}. */
    public static String soundName(int clip) {
        return "ghost.call" + (clip + 1);
    }

    /** The clip's .ogg file inside the mod jar. */
    private static String clipFile(int clip) {
        return "/assets/" + Constants.MOD_ID + "/sounds/ghost/call" + (clip + 1) + ".ogg";
    }

    private static int[] clipTicks() {
        if (clipTicks == null) {
            int[] ticks = new int[CLIP_COUNT];
            for (int i = 0; i < CLIP_COUNT; i++) {
                ticks[i] = FALLBACK_CLIP_TICKS;
                String file = clipFile(i);
                try (InputStream in = GhostCall.class.getResourceAsStream(file)) {
                    if (in == null) {
                        Constants.LOG.warn("Ghost call clip {} not found; assuming {} ticks", file, FALLBACK_CLIP_TICKS);
                    } else {
                        ticks[i] = Math.max(1, (int) Math.round(OggDuration.seconds(in) * 20.0));
                    }
                } catch (Exception e) {
                    Constants.LOG.warn("Couldn't read the length of ghost call clip {}; assuming {} ticks",
                            file, FALLBACK_CLIP_TICKS, e);
                }
            }
            clipTicks = ticks;
        }
        return clipTicks;
    }
}
