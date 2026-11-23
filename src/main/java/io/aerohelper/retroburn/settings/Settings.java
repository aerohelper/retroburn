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
 * @param video settings for video DVDs
 * @param design settings for the cover and label designer
 * @param appearance theme, sounds and the disc animation
 * @param reel burn reel options
 */
public record Settings(
        boolean preventSleepWhileBurning,
        boolean keepTemporaryFiles,
        Optional<Path> workDirectory,
        Map<ExternalTool, Path> toolPaths,
        boolean verifyAfterBurn,
        boolean showUsbTip,
        Audio audio,
        Mp3 mp3,
        Video video,
        Design design,
        Appearance appearance,
        Reel reel) {

    public Settings {
        Objects.requireNonNull(workDirectory, "workDirectory");
        toolPaths = Map.copyOf(toolPaths);
        Objects.requireNonNull(audio, "audio");
        Objects.requireNonNull(mp3, "mp3");
        Objects.requireNonNull(video, "video");
        Objects.requireNonNull(design, "design");
        Objects.requireNonNull(appearance, "appearance");
        Objects.requireNonNull(reel, "reel");
    }

    /**
     * Video DVD settings, kept as names like {@link Mp3}.
     *
     * @param tvStandard PAL or NTSC; empty for the computer's country
     * @param chapterMinutes a chapter every so many minutes, 0 for none
     * @param template classic, y2k or minimal
     * @param afterTitle MENU or NEXT
     */
    public record Video(String tvStandard, int chapterMinutes, String template, String afterTitle) {

        public Video {
            Objects.requireNonNull(tvStandard, "tvStandard");
            Objects.requireNonNull(template, "template");
            Objects.requireNonNull(afterTitle, "afterTitle");
        }

        public static Video defaults() {
            return new Video("", 5, "classic", "MENU");
        }
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

    /**
     * Cover and label designer settings.
     *
     * @param paper A4 or LETTER; empty for the computer's country
     * @param imageNoticeShown whether the note about image rights has been shown once
     */
    public record Design(String paper, boolean imageNoticeShown) {

        public Design {
            Objects.requireNonNull(paper, "paper");
        }

        public static Design defaults() {
            return new Design("", false);
        }
    }

    /**
     * Theme, sounds and the spinning disc.
     *
     * @param theme theme id, such as classic or y2k
     * @param sounds ON or OFF; empty to follow the theme's default
     * @param animateDisc whether the disc on the progress screen moves
     */
    public record Appearance(String theme, String sounds, boolean animateDisc) {

        public Appearance {
            Objects.requireNonNull(theme, "theme");
            Objects.requireNonNull(sounds, "sounds");
        }

        public static Appearance defaults() {
            return new Appearance("classic", "", true);
        }

        /** The sound choice as a flag, or null when it follows the theme. */
        public Boolean soundChoice() {
            return switch (sounds) {
                case "ON" -> Boolean.TRUE;
                case "OFF" -> Boolean.FALSE;
                default -> null;
            };
        }
    }

    /**
     * Burn reel options.
     *
     * @param hideNames show "Item 1", "Item 2" … instead of file, track and title names
     * @param seconds reel length, 12 to 18
     */
    public record Reel(boolean hideNames, int seconds) {

        public static Reel defaults() {
            return new Reel(true, 15);
        }
    }

    /** Settings for a first start. */
    public static Settings defaults() {
        return new Settings(
                true,
                false,
                Optional.empty(),
                Map.of(),
                true,
                true,
                Audio.defaults(),
                Mp3.defaults(),
                Video.defaults(),
                Design.defaults(),
                Appearance.defaults(),
                Reel.defaults());
    }

    public Settings withVerifyAfterBurn(boolean verify) {
        return new Settings(
                preventSleepWhileBurning,
                keepTemporaryFiles,
                workDirectory,
                toolPaths,
                verify,
                showUsbTip,
                audio,
                mp3,
                video,
                design,
                appearance,
                reel);
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
                mp3,
                video,
                design,
                appearance,
                reel);
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
                mp3,
                video,
                design,
                appearance,
                reel);
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
                changed,
                video,
                design,
                appearance,
                reel);
    }

    public Settings withVideo(Video changed) {
        return new Settings(
                preventSleepWhileBurning,
                keepTemporaryFiles,
                workDirectory,
                toolPaths,
                verifyAfterBurn,
                showUsbTip,
                audio,
                mp3,
                changed,
                design,
                appearance,
                reel);
    }

    public Settings withDesign(Design changed) {
        return new Settings(
                preventSleepWhileBurning,
                keepTemporaryFiles,
                workDirectory,
                toolPaths,
                verifyAfterBurn,
                showUsbTip,
                audio,
                mp3,
                video,
                changed,
                appearance,
                reel);
    }

    public Settings withAppearance(Appearance changed) {
        return new Settings(
                preventSleepWhileBurning,
                keepTemporaryFiles,
                workDirectory,
                toolPaths,
                verifyAfterBurn,
                showUsbTip,
                audio,
                mp3,
                video,
                design,
                changed,
                reel);
    }

    public Settings withReel(Reel changed) {
        return new Settings(
                preventSleepWhileBurning,
                keepTemporaryFiles,
                workDirectory,
                toolPaths,
                verifyAfterBurn,
                showUsbTip,
                audio,
                mp3,
                video,
                design,
                appearance,
                changed);
    }
}
