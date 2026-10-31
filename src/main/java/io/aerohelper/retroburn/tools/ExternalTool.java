// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.tools;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The external programs RetroBurn depends on, with how to ask each one for its version.
 *
 * <p>Version commands and patterns were checked against real output, captured in {@code
 * src/test/resources/tool-output/}; see {@code docs/design/LLD-tools.md} section 4.
 */
public enum ExternalTool {
    /** GNU xorriso, https://www.gnu.org/software/xorriso/ — {@code -version} prints the version first. */
    XORRISO("xorriso", "GNU xorriso", List.of("-version"), "^xorriso (\\S+) :"),

    /** cdrskin from libburnia, https://dev.lovelyhq.com/libburnia/libburn — cdrecord-style {@code --version}. */
    CDRSKIN("cdrskin", "cdrskin", List.of("--version"), "^cdrskin (\\S+) :"),

    /** genisoimage from cdrkit — {@code --version} prints "genisoimage 1.1.11 (Linux)". */
    GENISOIMAGE("genisoimage", "genisoimage", List.of("--version"), "^genisoimage (\\S+)"),

    /** FFmpeg, https://ffmpeg.org/ffmpeg.html — {@code -version} prints "ffmpeg version X ...". */
    FFMPEG("ffmpeg", "FFmpeg", List.of("-version"), "^ffmpeg version (\\S+)"),

    /** ffprobe from FFmpeg, https://ffmpeg.org/ffprobe.html — same convention as ffmpeg. */
    FFPROBE("ffprobe", "ffprobe", List.of("-version"), "^ffprobe version (\\S+)"),

    /**
     * dvdauthor, https://github.com/ldo/dvdauthor — has no version option; {@code --help} prints
     * "DVDAuthor::dvdauthor, version 0.7.2." on its first line.
     */
    DVDAUTHOR("dvdauthor", "dvdauthor", List.of("--help"), "^DVDAuthor::dvdauthor, version (\\d[\\d.]*\\d)"),

    /** spumux from dvdauthor — same convention as dvdauthor. */
    SPUMUX("spumux", "spumux", List.of("--help"), "^DVDAuthor::spumux, version (\\d[\\d.]*\\d)");

    private final String executableName;
    private final String displayName;
    private final List<String> versionArguments;
    private final Pattern versionPattern;

    ExternalTool(String executableName, String displayName, List<String> versionArguments, String versionPattern) {
        this.executableName = executableName;
        this.displayName = displayName;
        this.versionArguments = versionArguments;
        this.versionPattern = Pattern.compile(versionPattern);
    }

    /** File name without extension, for example "xorriso". */
    public String executableName() {
        return executableName;
    }

    /** Name as the project spells it, for messages, for example "FFmpeg". */
    public String displayName() {
        return displayName;
    }

    /** Arguments that make the tool print its version. */
    public List<String> versionArguments() {
        return versionArguments;
    }

    /** Finds the version in the output of the version command, if this really is the tool. */
    public Optional<String> parseVersion(List<String> outputLines) {
        for (String line : outputLines) {
            Matcher matcher = versionPattern.matcher(line);
            if (matcher.find()) {
                return Optional.of(matcher.group(1));
            }
        }
        return Optional.empty();
    }
}
