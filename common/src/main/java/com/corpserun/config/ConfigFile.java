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
        double value = fallback;
        String raw = this.props.getProperty(key);
        if (raw != null) {
            try {
                value = Math.max(0.0, Double.parseDouble(raw.trim()));
            } catch (NumberFormatException e) {
                warnInvalid(key, raw, fallback);
            }
        }
        addEntry(key, ConfigSetting.Kind.DOUBLE, fallback, value, comment);
        return value;
    }

    /** A whole number of at least {@code min}. */
    int getInt(String key, int fallback, int min, String comment) {
        int value = fallback;
        String raw = this.props.getProperty(key);
        if (raw != null) {
            try {
                value = Math.max(min, Integer.parseInt(raw.trim()));
            } catch (NumberFormatException e) {
                warnInvalid(key, raw, fallback);
            }
        }
        addEntry(key, ConfigSetting.Kind.INT, fallback, value, comment);
        return value;
    }

    /** {@code true} or {@code false} (any case); anything else falls back to the default. */
    boolean getBoolean(String key, boolean fallback, String comment) {
        boolean value = fallback;
        String raw = this.props.getProperty(key);
        if (raw != null) {
            String trimmed = raw.trim();
            if (trimmed.equalsIgnoreCase("true")) {
                value = true;
            } else if (trimmed.equalsIgnoreCase("false")) {
                value = false;
            } else {
                warnInvalid(key, raw, fallback);
            }
        }
        addEntry(key, ConfigSetting.Kind.BOOLEAN, fallback, value, comment);
        return value;
    }

    /** Every setting read so far, in order, with the value in effect (what the getters returned). */
    List<ConfigSetting> settings() {
        return this.entries.stream()
                .map(entry -> new ConfigSetting(entry.key(), entry.kind(), entry.value(), entry.fallback(), entry.comment()))
                .toList();
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

    private void addEntry(String key, ConfigSetting.Kind kind, Object fallback, Object value, String comment) {
        this.entries.add(new Entry(key, kind, String.valueOf(fallback), String.valueOf(value), comment));
    }

    private void warnInvalid(String key, String raw, Object fallback) {
        Constants.LOG.warn("Invalid {} '{}' in {}, using {}", key, raw, this.path, fallback);
    }

    private record Entry(String key, ConfigSetting.Kind kind, String fallback, String value, String comment) {
    }
}
