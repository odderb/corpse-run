package com.corpserun.config;

import java.util.List;
import java.util.Map;

/**
 * Server (gameplay) settings, from {@code config/corpserun-server.properties}. Loaded on both dedicated servers and
 * clients (for singleplayer). Client-only settings live in {@link CorpseRunClientConfig}.
 */
public final class CorpseRunConfig {

    public static final String FILE_NAME = "corpserun-server.properties";

    private static double resurrectRange = 10.0;
    private static boolean footprints = true;
    private static boolean footprintsOnlyWhenHowling = false;
    private static boolean corpseHum = true;
    private static double humRange = 48.0;
    private static double ghostSpeedPercent = 150.0;
    private static int trailMaxPoints = 4096;
    private static double guidanceLookahead = 24.0;
    private static double resurrectProtectionSeconds = 10.0;
    private static double resurrectKnockbackPercent = 1000.0;
    private static double resurrectHitSlowSeconds = 3.0;
    private static int etherealBlockLimit = 3;
    private static double wanderDistance = 64.0;
    private static boolean ghostsVisibleToLiving = false;
    private static boolean footstepPathfinding = true;
    private static boolean etherealBlocks = true;
    private static boolean ghostFlight = false;
    private static boolean sculkIgnoresGhosts = true;
    private static boolean howlTriggersSculk = true;

    private CorpseRunConfig() {
    }

    /** Max distance in blocks from the corpse at which a ghost may /resurrect. */
    public static double resurrectRange() {
        return resurrectRange;
    }

    public static boolean footprints() {
        return footprints;
    }

    public static boolean footprintsOnlyWhenHowling() {
        return footprintsOnlyWhenHowling;
    }

    public static boolean corpseHum() {
        return corpseHum;
    }

    public static double humRange() {
        return humRange;
    }

    /** Ghost movement speed as a percentage of normal (150 = 1.5x). */
    public static double ghostSpeedPercent() {
        return ghostSpeedPercent;
    }

    public static int trailMaxPoints() {
        return trailMaxPoints;
    }

    public static double guidanceLookahead() {
        return guidanceLookahead;
    }

    public static double resurrectProtectionSeconds() {
        return resurrectProtectionSeconds;
    }

    public static double resurrectKnockbackPercent() {
        return resurrectKnockbackPercent;
    }

    public static double resurrectHitSlowSeconds() {
        return resurrectHitSlowSeconds;
    }

    public static int etherealBlockLimit() {
        return etherealBlockLimit;
    }

    public static boolean ghostsVisibleToLiving() {
        return ghostsVisibleToLiving;
    }

    public static double wanderDistance() {
        return wanderDistance;
    }

    /** Whether footsteps are pathfound from the ghost to its trail; false runs no path searches at all. */
    public static boolean footstepPathfinding() {
        return footstepPathfinding;
    }

    /** Whether ghosts can place ethereal blocks at all (etherealBlockLimit 0 does the same). */
    public static boolean etherealBlocks() {
        return etherealBlocks;
    }

    /** Whether ghosts may fly as in creative (flight only, not the game mode). */
    public static boolean ghostFlight() {
        return ghostFlight;
    }

    /** Whether sculk (and anything else listening for vibrations) ignores everything ghosts do, except howling. */
    public static boolean sculkIgnoresGhosts() {
        return sculkIgnoresGhosts;
    }

    /** Whether a howl makes a vibration that sets sculk off, like a goat horn. */
    public static boolean howlTriggersSculk() {
        return howlTriggersSculk;
    }

    public static void load() {
        ConfigFile file = open();
        read(file);
        file.writeDefaultsIfMissing();
    }

    /**
     * Every setting with its value, for the server settings screen. Re-reads the file, so edits made to it by hand
     * since startup are picked up (and applied) here too.
     */
    public static List<ConfigSetting> settings() {
        ConfigFile file = open();
        read(file);
        return file.settings();
    }

    /**
     * Applies new values (by key, as text, as in the file) from the server settings screen and saves them. Anything
     * invalid falls back to its default, as when loading. Call on the server thread when a server is running, since
     * the server reads these as it ticks.
     */
    public static void update(Map<String, String> values) {
        ConfigFile file = open();
        values.forEach(file::set);
        // Read back through the same parsing as the file, so the values are clamped the same way.
        read(file);
        file.save();
    }

    private static ConfigFile open() {
        return new ConfigFile(FILE_NAME, "CorpseRun server settings");
    }

    private static void read(ConfigFile file) {
        resurrectRange = file.getDouble("resurrectRange", 10.0,
                "Distance in blocks from your corpse within which you can /resurrect");
        footprints = file.getBoolean("footprints", true,
                "Ghosts see footprints guiding them back along the path they walked before dying");
        footprintsOnlyWhenHowling = file.getBoolean("footprintsOnlyWhenHowling", false,
                "Footprints only appear while the ghost howls; otherwise they keep repeating");
        trailMaxPoints = file.getInt("trailMaxPoints", 4096, 2,
                "Recorded path points (about 2 blocks apart) kept per life, oldest dropped first");
        guidanceLookahead = file.getDouble("guidanceLookahead", 24.0,
                "How many blocks of footprints are shown ahead of a ghost");
        corpseHum = file.getBoolean("corpseHum", true,
                "Ghosts hear a low hum coming from their corpse");
        humRange = file.getDouble("humRange", 48.0,
                "Distance in blocks within which the hum is audible");
        ghostSpeedPercent = file.getDouble("ghostSpeedPercent", 150.0,
                "Ghost walking speed as a percentage of normal, e.g. 150 = 1.5x");
        resurrectProtectionSeconds = file.getDouble("resurrectProtectionSeconds", 10.0,
                "Seconds of invulnerability after /resurrect");
        resurrectKnockbackPercent = file.getDouble("resurrectKnockbackPercent", 1000.0,
                "Knockback of your hits during that protection, as a percentage of normal");
        resurrectHitSlowSeconds = file.getDouble("resurrectHitSlowSeconds", 3.0,
                "Seconds of Slowness III (45% slower) on whatever you hit during that protection; 0 disables");
        etherealBlockLimit = file.getInt("etherealBlockLimit", 3, 0,
                "How many ethereal blocks a ghost can have at once; placing another removes the oldest, 0 disables");
        ghostsVisibleToLiving = file.getBoolean("ghostsVisibleToLiving", false,
                "Whether living players can see ghosts (and their notes); false = only ghosts see ghosts");
        wanderDistance = file.getDouble("wanderDistance", 64.0,
                "Blocks from the nearest point of the trail back before a ghost is offered /corpse reset, 0 disables");
        footstepPathfinding = file.getBoolean("footstepPathfinding", true,
                "Footsteps pathfind from the ghost back to its trail; false only shows them along the recorded trail and runs no path searches, to save server performance");
        etherealBlocks = file.getBoolean("etherealBlocks", true,
                "Ghosts can place ethereal blocks by right-clicking with an empty hand");
        ghostFlight = file.getBoolean("ghostFlight", false,
                "Ghosts can fly like in creative mode (double-tap jump); only the flight, not the game mode");
        sculkIgnoresGhosts = file.getBoolean("sculkIgnoresGhosts", true,
                "Sculk sensors, shriekers and wardens don't notice ghosts walking, landing, swimming, opening doors...");
        howlTriggersSculk = file.getBoolean("howlTriggersSculk", true,
                "A ghost's howl sets off sculk like a goat horn, even when sculk ignores ghosts otherwise");
    }
}
