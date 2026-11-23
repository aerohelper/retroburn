// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.settings;

import io.aerohelper.retroburn.tools.ExternalTool;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The user's settings. Immutable: changing a setting creates a new instance.
 *
 * <p>More settings (theme, sounds, verify, TV standard, ...) are added with the features that use
 * them. The file format is described in {@code docs/DATA_MODEL.md}.
 *
 * @param preventSleepWhileBurning keep the computer awake while writing a disc
 * @param keepTemporaryFiles keep job work folders after success or cancel
 * @param workDirectory folder for job work folders instead of the default
 * @param toolPaths user-chosen executables for external tools
 * @param verifyAfterBurn compare the disc with what was written after each burn
 * @param showUsbTip show the advice for USB drives before burning
 * @param audio settings for audio CDs
 * @param mp3 settings for MP3 discs
 */
public record Settings(
        boolean preventSleepWhileBurning,
        boolean keepTemporaryFiles,
        Optional<Path> workDirectory,
        Map<ExternalTool, Path> toolPaths,
        boolean verifyAfterBurn,
        boolean showUsbTip,
        Audio audio,
        Mp3 mp3) {

    public Settings {
        Objects.requireNonNull(workDirectory, "workDirectory");
        toolPaths = Map.copyOf(toolPaths);
        Objects.requireNonNull(audio, "audio");
        Objects.requireNonNull(mp3, "mp3");
    }

    /**
     * MP3 disc settings. Layout and bitrate are kept as names ({@code project.Mp3Layout}, {@code
     * audio.Mp3Encoding.Bitrate}); the screen falls back to the default for a name it does not know.
     *
     * @param layout where files go: KEEP_FOLDERS, ARTIST_ALBUM or FLAT
     * @param carCompat car stereo naming and depth rules
     * @param convert convert other formats to MP3
     * @param bitrate CBR_128, CBR_192, CBR_256, CBR_320 or VBR_2
     * @param playlists write one playlist per folder
     */
    public record Mp3(String layout, boolean carCompat, boolean convert, String bitrate, boolean playlists) {

        public Mp3 {
            Objects.requireNonNull(layout, "layout");
            Objects.requireNonNull(bitrate, "bitrate");
        }

        public static Mp3 defaults() {
            return new Mp3("KEEP_FOLDERS", true, true, "CBR_192", false);
        }
    }

    /**
     * Audio CD settings.
     *
     * @param cdText write CD-Text when the system can
     * @param normalizeLoudness even out loudness while converting tracks
     */
    public record Audio(boolean cdText, boolean normalizeLoudness) {

        public static Audio defaults() {
            return new Audio(true, false);
        }
    }

    /** Settings for a first start. */
    public static Settings defaults() {
        return new Settings(true, false, Optional.empty(), Map.of(), true, true, Audio.defaults(), Mp3.defaults());
    }

    public Settings withVerifyAfterBurn(boolean verify) {
        return new Settings(
                preventSleepWhileBurning, keepTemporaryFiles, workDirectory, toolPaths, verify, showUsbTip, audio, mp3);
    }

    public Settings withShowUsbTip(boolean show) {
        return new Settings(
                preventSleepWhileBurning,
                keepTemporaryFiles,
                workDirectory,
                toolPaths,
                verifyAfterBurn,
                show,
                audio,
                mp3);
    }

    public Settings withAudio(Audio changed) {
        return new Settings(
                preventSleepWhileBurning,
                keepTemporaryFiles,
                workDirectory,
                toolPaths,
                verifyAfterBurn,
                showUsbTip,
                changed,
                mp3);
    }

    public Settings withMp3(Mp3 changed) {
        return new Settings(
                preventSleepWhileBurning,
                keepTemporaryFiles,
                workDirectory,
                toolPaths,
                verifyAfterBurn,
                showUsbTip,
                audio,
                changed);
    }
}
