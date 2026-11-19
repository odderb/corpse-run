package com.corpserun.config;

/**
 * Client-only (visual) settings, from {@code config/corpserun-client.properties}. Loaded by the loaders' client
 * entry points only, so dedicated servers never create this file.
 */
public final class CorpseRunClientConfig {

    public static final String FILE_NAME = "corpserun-client.properties";

    private static boolean ghostScreenEffect = true;
    private static boolean ghostHideHud = true;

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

    public static void load() {
        ConfigFile file = new ConfigFile(FILE_NAME, "CorpseRun client settings");
        ghostScreenEffect = file.getBoolean("ghostScreenEffect", true,
                "Show full-screen static while you are a ghost");
        ghostHideHud = file.getBoolean("ghostHideHud", true,
                "Hide the hotbar, health, armor, hunger, air and XP bars while you are a ghost");
        file.writeDefaultsIfMissing();
    }
}
