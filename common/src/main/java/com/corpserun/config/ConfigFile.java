package com.corpserun.config;

import com.corpserun.Constants;
import com.corpserun.platform.Services;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * One {@code .properties} file in the config folder. An existing file is only ever read, never rewritten: missing or
 * invalid values fall back to their defaults (with a warning for invalid ones). When the file doesn't exist yet, a
 * commented one with every default is written once by {@link #writeDefaultsIfMissing()}.
 */
final class ConfigFile {

    private final Path path;
    private final boolean exists;
    private final Properties props = new Properties();
    private final StringBuilder defaults;

    ConfigFile(String fileName, String header) {
        this.path = Services.PLATFORM.getConfigDir().resolve(fileName);
        this.exists = Files.exists(this.path);
        this.defaults = new StringBuilder("# ").append(header).append('\n');
        if (this.exists) {
            try (Reader reader = Files.newBufferedReader(this.path)) {
                this.props.load(reader);
            } catch (IOException e) {
                Constants.LOG.error("Failed to read {}, using defaults", this.path, e);
            }
        }
    }

    /** A number of at least 0. */
    double getDouble(String key, double fallback, String comment) {
        addDefault(key, fallback, comment);
        String raw = this.props.getProperty(key);
        if (raw != null) {
            try {
                return Math.max(0.0, Double.parseDouble(raw.trim()));
            } catch (NumberFormatException e) {
                warnInvalid(key, raw, fallback);
            }
        }
        return fallback;
    }

    /** A whole number of at least {@code min}. */
    int getInt(String key, int fallback, int min, String comment) {
        addDefault(key, fallback, comment);
        String raw = this.props.getProperty(key);
        if (raw != null) {
            try {
                return Math.max(min, Integer.parseInt(raw.trim()));
            } catch (NumberFormatException e) {
                warnInvalid(key, raw, fallback);
            }
        }
        return fallback;
    }

    /** {@code true} or {@code false} (any case); anything else falls back to the default. */
    boolean getBoolean(String key, boolean fallback, String comment) {
        addDefault(key, fallback, comment);
        String raw = this.props.getProperty(key);
        if (raw != null) {
            String value = raw.trim();
            if (value.equalsIgnoreCase("true")) {
                return true;
            }
            if (value.equalsIgnoreCase("false")) {
                return false;
            }
            warnInvalid(key, raw, fallback);
        }
        return fallback;
    }

    /** Writes the commented defaults collected by the getters, but only if the file doesn't exist yet. */
    void writeDefaultsIfMissing() {
        if (this.exists) {
            return;
        }
        try {
            Files.createDirectories(this.path.getParent());
            Files.writeString(this.path, this.defaults);
        } catch (IOException e) {
            Constants.LOG.error("Failed to write {}", this.path, e);
        }
    }

    private void addDefault(String key, Object value, String comment) {
        this.defaults.append('\n').append("# ").append(comment).append('\n').append(key).append('=').append(value).append('\n');
    }

    private void warnInvalid(String key, String raw, Object fallback) {
        Constants.LOG.warn("Invalid {} '{}' in {}, using {}", key, raw, this.path, fallback);
    }
}
