package com.corpserun.config;

/**
 * Server (gameplay) settings, from {@code config/corpserun-server.properties}. Loaded on both dedicated servers and
 * clients (for singleplayer). Client-only settings live in {@link CorpseRunClientConfig}.
 */
public final class CorpseRunConfig {

    public static final String FILE_NAME = "corpserun-server.properties";

    private static double resurrectRange = 10.0;
    private static boolean footprints = true;
    private static boolean corpseHum = true;
    private static double humRange = 48.0;
    private static double ghostSpeedPercent = 150.0;
    private static int trailMaxPoints = 4096;
    private static double guidanceLookahead = 24.0;
    private static double resurrectProtectionSeconds = 10.0;
    private static double resurrectKnockbackPercent = 300.0;
    private static int etherealBlockLimit = 3;
    private static double wanderDistance = 64.0;
    private static boolean ghostsVisibleToLiving = false;

    private CorpseRunConfig() {
    }

    /** Max distance in blocks from the corpse at which a ghost may /resurrect. */
    public static double resurrectRange() {
        return resurrectRange;
    }

    public static boolean footprints() {
        return footprints;
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

    public static int etherealBlockLimit() {
        return etherealBlockLimit;
    }

    public static boolean ghostsVisibleToLiving() {
        return ghostsVisibleToLiving;
    }

    public static double wanderDistance() {
        return wanderDistance;
    }

    public static void load() {
        ConfigFile file = new ConfigFile(FILE_NAME, "CorpseRun server settings");
        resurrectRange = file.getDouble("resurrectRange", 10.0,
                "Distance in blocks from your corpse within which you can /resurrect");
        footprints = file.getBoolean("footprints", true,
                "Ghosts see footprints guiding them back along the path they walked before dying");
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
        resurrectKnockbackPercent = file.getDouble("resurrectKnockbackPercent", 300.0,
                "Knockback of your hits during that protection, as a percentage of normal");
        etherealBlockLimit = file.getInt("etherealBlockLimit", 3, 0,
                "How many ethereal blocks a ghost can have at once; placing another removes the oldest, 0 disables");
        ghostsVisibleToLiving = file.getBoolean("ghostsVisibleToLiving", false,
                "Whether living players can see ghosts (and their notes); false = only ghosts see ghosts");
        wanderDistance = file.getDouble("wanderDistance", 64.0,
                "Blocks from the nearest point of the trail back before a ghost is offered /corpse reset, 0 disables");
        file.writeDefaultsIfMissing();
    }
}
