package com.corpserun.config;

/**
 * One setting of a config file as the config screens show it.
 *
 * @param value        the value in effect, as it would be written to the file
 * @param defaultValue the default, in the same form
 * @param comment      the file comment, also used as the tooltip when there's no translation
 */
public record ConfigSetting(String key, Kind kind, String value, String defaultValue, String comment) {

    public enum Kind {
        BOOLEAN, INT, DOUBLE
    }
}
