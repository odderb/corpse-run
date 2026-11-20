package com.corpserun.config;

import com.corpserun.Constants;
import com.corpserun.platform.Services;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * One {@code .properties} file in the config folder. Missing or invalid values fall back to their defaults (with a
 * warning for invalid ones). When the file doesn't exist yet, a commented one with every default is written once by
 * {@link #writeDefaultsIfMissing()}; for an existing file, that only appends keys it doesn't have yet (settings added
 * in a later version), leaving everything already in it untouched. An existing file is only rewritten by
 * {@link #save()}, for the in-game config screen.
 */
final class ConfigFile {

    private final Path path;
    private final boolean exists;
    /** False if the existing file couldn't be read: then nothing is known to be missing, so nothing is appended. */
    private boolean readOk = true;
    private final Properties props = new Properties();
    private final String header;
    /** Every key read so far, in order, with its comment and default: what gets written back. */
    private final List<Entry> entries = new ArrayList<>();

    ConfigFile(String fileName, String header) {
        this.path = Services.PLATFORM.getConfigDir().resolve(fileName);
        this.exists = Files.exists(this.path);
        this.header = header;
        if (this.exists) {
            try (Reader reader = Files.newBufferedReader(this.path)) {
                this.props.load(reader);
            } catch (IOException e) {
                this.readOk = false;
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

    /** Replaces a value before it's read, as if the file held it. Written out by {@link #save()}. */
    void set(String key, Object value) {
        this.props.setProperty(key, String.valueOf(value));
    }

    /**
     * Writes the commented defaults collected by the getters if the file doesn't exist yet. If it does, appends only
     * the keys it doesn't have, each with its comment and default; what's already in the file is never changed.
     */
    void writeDefaultsIfMissing() {
        if (!this.exists) {
            write(false);
        } else if (this.readOk) {
            appendMissing();
        }
    }

    private void appendMissing() {
        StringBuilder text = new StringBuilder();
        for (Entry entry : this.entries) {
            if (!this.props.containsKey(entry.key())) {
                text.append('\n').append("# ").append(entry.comment())
                        .append('\n').append(entry.key()).append('=').append(entry.fallback()).append('\n');
            }
        }
        if (text.isEmpty()) {
            return;
        }
        try {
            // Start on a fresh line if the file doesn't end with one, so the first key isn't glued to its last line.
            String existing = Files.readString(this.path);
            if (!existing.isEmpty() && !existing.endsWith("\n")) {
                text.insert(0, '\n');
            }
            Files.writeString(this.path, text, StandardOpenOption.APPEND);
            Constants.LOG.info("Added new settings with their defaults to {}", this.path);
        } catch (IOException e) {
            Constants.LOG.error("Failed to add new settings to {}", this.path, e);
        }
    }

    /** Rewrites the file with the current value of every key read so far, each commented with its default. */
    void save() {
        write(true);
    }

    private void write(boolean currentValues) {
        StringBuilder text = new StringBuilder("# ").append(this.header).append('\n');
        for (Entry entry : this.entries) {
            text.append('\n').append("# ").append(entry.comment());
            String value = entry.fallback();
            if (currentValues) {
                text.append(" (default ").append(entry.fallback()).append(')');
                value = this.props.getProperty(entry.key(), entry.fallback()).trim();
            }
            text.append('\n').append(entry.key()).append('=').append(value).append('\n');
        }
        try {
            Files.createDirectories(this.path.getParent());
            Files.writeString(this.path, text);
        } catch (IOException e) {
            Constants.LOG.error("Failed to write {}", this.path, e);
        }
    }

    private void addDefault(String key, Object value, String comment) {
        this.entries.add(new Entry(key, String.valueOf(value), comment));
    }

    private void warnInvalid(String key, String raw, Object fallback) {
        Constants.LOG.warn("Invalid {} '{}' in {}, using {}", key, raw, this.path, fallback);
    }

    private record Entry(String key, String fallback, String comment) {
    }
}
