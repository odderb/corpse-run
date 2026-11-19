package com.corpserun.config;

/**
 * Client-only (visual) settings, from {@code config/corpserun-client.properties}. Loaded by the loaders' client
 * entry points only, so dedicated servers never create this file.
 */
public final class CorpseRunClientConfig {

    public static final String FILE_NAME = "corpserun-client.properties";

    private static boolean ghostScreenEffect = true;
    private static boolean ghostHideHud = true;
    private static float ghostNightVision = 0.5F;

    private CorpseRunClientConfig() {
    }

    /** Whether ghosts see the full-screen static effect. */
    public static boolean ghostScreenEffect() {
        return ghostScreenEffect;
    }

    /** Whether the hotbar, health, armor, hunger, air and XP bars are hidden while you are a ghost. */
    public static boolean ghostHideHud() {
        return ghostHideHud;
    }

    /**
     * How much ghosts see in the dark, from 0 (off) to 1 (as bright as the Night Vision effect). Real Night Vision
     * still applies at full strength if it's stronger.
     */
    public static float ghostNightVision() {
        return ghostNightVision;
    }

    public static void load() {
        ConfigFile file = open();
        read(file);
        file.writeDefaultsIfMissing();
    }

    /**
     * Applies new values from the config screen and saves them. They take effect right away: everything reads these
     * settings each frame.
     */
    public static void update(boolean screenEffect, boolean hideHud, float nightVision) {
        ConfigFile file = open();
        file.set("ghostScreenEffect", screenEffect);
        file.set("ghostHideHud", hideHud);
        file.set("ghostNightVision", nightVision);
        // Read back through the same parsing as the file, so the values are clamped the same way.
        read(file);
        file.save();
    }

    private static ConfigFile open() {
        return new ConfigFile(FILE_NAME, "CorpseRun client settings");
    }

    private static void read(ConfigFile file) {
        ghostScreenEffect = file.getBoolean("ghostScreenEffect", true,
                "Show full-screen static while you are a ghost");
        ghostHideHud = file.getBoolean("ghostHideHud", true,
                "Hide the hotbar, health, armor, hunger, air and XP bars while you are a ghost");
        ghostNightVision = (float) Math.min(1.0, file.getDouble("ghostNightVision", 0.5,
                "How well you see in the dark while you are a ghost, from 0 (off) to 1 (full Night Vision)"));
    }
}
