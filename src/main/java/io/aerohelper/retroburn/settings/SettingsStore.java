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
    static final String KEY_AUDIO_CD_TEXT = "audio.cdText";
    static final String KEY_AUDIO_NORMALIZE = "audio.normalizeLoudness";
    static final String KEY_MP3_LAYOUT = "mp3.layout";
    static final String KEY_MP3_CAR = "mp3.carCompat";
    static final String KEY_MP3_CONVERT = "mp3.convert";
    static final String KEY_MP3_BITRATE = "mp3.bitrate";
    static final String KEY_MP3_PLAYLISTS = "mp3.playlists";
    static final String KEY_VIDEO_STANDARD = "video.tvStandard";
    static final String KEY_VIDEO_CHAPTERS = "video.chapterMinutes";
    static final String KEY_VIDEO_TEMPLATE = "video.template";
    static final String KEY_VIDEO_AFTER = "video.afterTitle";
    static final String KEY_DESIGN_PAPER = "design.paper";
    static final String KEY_DESIGN_IMAGE_NOTICE = "design.imageNoticeShown";
    static final String KEY_THEME = "appearance.theme";
    static final String KEY_SOUNDS = "appearance.sounds";
    static final String KEY_ANIMATE_DISC = "appearance.animateDisc";

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
                bool(properties.getProperty(KEY_SHOW_USB_TIP), defaults.showUsbTip()),
                new Settings.Audio(
                        bool(
                                properties.getProperty(KEY_AUDIO_CD_TEXT),
                                defaults.audio().cdText()),
                        bool(
                                properties.getProperty(KEY_AUDIO_NORMALIZE),
                                defaults.audio().normalizeLoudness())),
                new Settings.Mp3(
                        properties.getProperty(KEY_MP3_LAYOUT, defaults.mp3().layout()),
                        bool(properties.getProperty(KEY_MP3_CAR), defaults.mp3().carCompat()),
                        bool(
                                properties.getProperty(KEY_MP3_CONVERT),
                                defaults.mp3().convert()),
                        properties.getProperty(KEY_MP3_BITRATE, defaults.mp3().bitrate()),
                        bool(
                                properties.getProperty(KEY_MP3_PLAYLISTS),
                                defaults.mp3().playlists())),
                new Settings.Video(
                        properties.getProperty(
                                KEY_VIDEO_STANDARD, defaults.video().tvStandard()),
                        integer(
                                properties.getProperty(KEY_VIDEO_CHAPTERS),
                                defaults.video().chapterMinutes()),
                        properties.getProperty(
                                KEY_VIDEO_TEMPLATE, defaults.video().template()),
                        properties.getProperty(KEY_VIDEO_AFTER, defaults.video().afterTitle())),
                new Settings.Design(
                        properties.getProperty(
                                KEY_DESIGN_PAPER, defaults.design().paper()),
                        bool(
                                properties.getProperty(KEY_DESIGN_IMAGE_NOTICE),
                                defaults.design().imageNoticeShown())),
                new Settings.Appearance(
                        properties.getProperty(KEY_THEME, defaults.appearance().theme()),
                        properties.getProperty(KEY_SOUNDS, defaults.appearance().sounds()),
                        bool(
                                properties.getProperty(KEY_ANIMATE_DISC),
                                defaults.appearance().animateDisc())));
    }

    private static int integer(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    static Properties toProperties(Settings settings) {
        Properties properties = new Properties();
        properties.setProperty(KEY_SCHEMA_VERSION, Integer.toString(SCHEMA_VERSION));
        properties.setProperty(KEY_PREVENT_SLEEP, Boolean.toString(settings.preventSleepWhileBurning()));
        properties.setProperty(KEY_KEEP_TEMPORARY_FILES, Boolean.toString(settings.keepTemporaryFiles()));
        properties.setProperty(KEY_VERIFY_AFTER_BURN, Boolean.toString(settings.verifyAfterBurn()));
        properties.setProperty(KEY_SHOW_USB_TIP, Boolean.toString(settings.showUsbTip()));
        properties.setProperty(
                KEY_AUDIO_CD_TEXT, Boolean.toString(settings.audio().cdText()));
        properties.setProperty(
                KEY_AUDIO_NORMALIZE, Boolean.toString(settings.audio().normalizeLoudness()));
        properties.setProperty(KEY_MP3_LAYOUT, settings.mp3().layout());
        properties.setProperty(KEY_MP3_CAR, Boolean.toString(settings.mp3().carCompat()));
        properties.setProperty(KEY_MP3_CONVERT, Boolean.toString(settings.mp3().convert()));
        properties.setProperty(KEY_MP3_BITRATE, settings.mp3().bitrate());
        properties.setProperty(
                KEY_MP3_PLAYLISTS, Boolean.toString(settings.mp3().playlists()));
        properties.setProperty(KEY_VIDEO_STANDARD, settings.video().tvStandard());
        properties.setProperty(
                KEY_VIDEO_CHAPTERS, Integer.toString(settings.video().chapterMinutes()));
        properties.setProperty(KEY_VIDEO_TEMPLATE, settings.video().template());
        properties.setProperty(KEY_VIDEO_AFTER, settings.video().afterTitle());
        properties.setProperty(KEY_DESIGN_PAPER, settings.design().paper());
        properties.setProperty(
                KEY_DESIGN_IMAGE_NOTICE, Boolean.toString(settings.design().imageNoticeShown()));
        properties.setProperty(KEY_THEME, settings.appearance().theme());
        properties.setProperty(KEY_SOUNDS, settings.appearance().sounds());
        properties.setProperty(
                KEY_ANIMATE_DISC, Boolean.toString(settings.appearance().animateDisc()));
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
