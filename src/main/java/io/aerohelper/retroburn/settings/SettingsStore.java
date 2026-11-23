// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.settings;

import io.aerohelper.retroburn.tools.ExternalTool;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads and saves {@link Settings} as {@code settings.properties} in the settings folder.
 *
 * <p>Unknown keys are ignored and invalid values fall back to defaults, so a hand-edited or newer
 * file never stops the app from starting. Thread-safe: {@link #current()} may be read from any
 * thread. See {@code docs/DATA_MODEL.md} for the keys.
 */
public final class SettingsStore {

    static final String FILE_NAME = "settings.properties";
    static final int SCHEMA_VERSION = 1;

    static final String KEY_SCHEMA_VERSION = "schemaVersion";
    static final String KEY_PREVENT_SLEEP = "burn.preventSleep";
    static final String KEY_KEEP_TEMPORARY_FILES = "jobs.keepTemporaryFiles";
    static final String KEY_WORK_DIRECTORY = "jobs.workDirectory";
    static final String KEY_TOOL_PATH_PREFIX = "tools.path.";
    static final String KEY_VERIFY_AFTER_BURN = "burn.verify";
    static final String KEY_SHOW_USB_TIP = "burn.showUsbTip";

    private static final Logger LOG = Logger.getLogger(SettingsStore.class.getName());

    private final Path file;
    private volatile Settings current;

    /** @param settingsFolder the platform's settings folder */
    public SettingsStore(Path settingsFolder) {
        this.file = Objects.requireNonNull(settingsFolder, "settingsFolder").resolve(FILE_NAME);
        this.current = load();
    }

    public Settings current() {
        return current;
    }

    /**
     * Stores new settings and makes them current. Writes to a temporary file first so a crash cannot
     * leave a half-written settings file.
     *
     * @throws UncheckedIOException if the file cannot be written
     */
    public void save(Settings settings) {
        Properties properties = toProperties(settings);
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(FILE_NAME + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                properties.store(writer, "RetroBurn settings");
            }
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save " + file, e);
        }
        current = settings;
    }

    private Settings load() {
        if (!Files.isRegularFile(file)) {
            return Settings.defaults();
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException | IllegalArgumentException e) {
            LOG.log(Level.WARNING, "Could not read " + file + "; using default settings", e);
            return Settings.defaults();
        }
        return fromProperties(properties);
    }

    static Settings fromProperties(Properties properties) {
        Settings defaults = Settings.defaults();
        Map<ExternalTool, Path> toolPaths = new EnumMap<>(ExternalTool.class);
        for (ExternalTool tool : ExternalTool.values()) {
            path(properties.getProperty(KEY_TOOL_PATH_PREFIX + toolKey(tool)))
                    .ifPresent(path -> toolPaths.put(tool, path));
        }
        return new Settings(
                bool(properties.getProperty(KEY_PREVENT_SLEEP), defaults.preventSleepWhileBurning()),
                bool(properties.getProperty(KEY_KEEP_TEMPORARY_FILES), defaults.keepTemporaryFiles()),
                path(properties.getProperty(KEY_WORK_DIRECTORY)),
                toolPaths,
                bool(properties.getProperty(KEY_VERIFY_AFTER_BURN), defaults.verifyAfterBurn()),
                bool(properties.getProperty(KEY_SHOW_USB_TIP), defaults.showUsbTip()));
    }

    static Properties toProperties(Settings settings) {
        Properties properties = new Properties();
        properties.setProperty(KEY_SCHEMA_VERSION, Integer.toString(SCHEMA_VERSION));
        properties.setProperty(KEY_PREVENT_SLEEP, Boolean.toString(settings.preventSleepWhileBurning()));
        properties.setProperty(KEY_KEEP_TEMPORARY_FILES, Boolean.toString(settings.keepTemporaryFiles()));
        properties.setProperty(KEY_VERIFY_AFTER_BURN, Boolean.toString(settings.verifyAfterBurn()));
        properties.setProperty(KEY_SHOW_USB_TIP, Boolean.toString(settings.showUsbTip()));
        settings.workDirectory().ifPresent(folder -> properties.setProperty(KEY_WORK_DIRECTORY, folder.toString()));
        settings.toolPaths()
                .forEach((tool, path) -> properties.setProperty(KEY_TOOL_PATH_PREFIX + toolKey(tool), path.toString()));
        return properties;
    }

    private static String toolKey(ExternalTool tool) {
        return tool.name().toLowerCase(Locale.ROOT);
    }

    private static boolean bool(String value, boolean fallback) {
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        return fallback;
    }

    private static Optional<Path> path(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Path.of(value.strip()));
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }
}
