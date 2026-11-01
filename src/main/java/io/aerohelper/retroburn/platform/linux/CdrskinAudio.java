// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.linux;

import io.aerohelper.retroburn.audio.CdAudio;
import io.aerohelper.retroburn.engine.DriveId;
import io.aerohelper.retroburn.engine.TrackLayout;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * cdrskin command lines and output for audio CDs. Options from the cdrskin 1.5.6 manual; output
 * formats from {@code cdrskin/cdrskin.c} in libburn and output captured on a {@code stdio:}
 * pseudo-drive. See {@code docs/design/LLD-audio-cd.md} section 6.1.
 */
final class CdrskinAudio {

    /**
     * The pacifier: {@code printf("%s%sTrack %-2.2d: %s MB written %s[buf %3d%%]  %4.1fx.%s", ...)}.
     * Captured: {@code Track 01:   10 of   20 MB written (fifo 100%) [buf  50%]  409.5x.}
     */
    private static final Pattern WRITTEN = Pattern.compile("Track (\\d+):\\s+(\\d+)(?: of\\s+\\d+)? MB written");

    /**
     * Table of contents: {@code printf("track:  %2d lba: %9d (%9d) %2.2d:%2.2d:%2.2d", ...)} per track
     * and {@code "track:lout lba: %9d ..."} for the lead-out.
     */
    private static final Pattern TOC_LINE = Pattern.compile("^track:\\s*(\\d+|lout) lba:\\s+(-?\\d+) ");

    private static final long MEBIBYTE = 1024 * 1024;

    private CdrskinAudio() {}

    /**
     * Arguments that write {@code wavFiles} as one SAO session with 2-second gaps, with CD-Text from
     * {@code sheet} when present.
     */
    static List<String> burn(DriveId drive, List<Path> wavFiles, Optional<Path> sheet) {
        List<String> arguments = new ArrayList<>(List.of("-v", "dev=" + drive.value(), "-sao"));
        sheet.ifPresent(path -> arguments.add("input_sheet_v07t=" + path));
        arguments.add("-audio");
        for (int index = 0; index < wavFiles.size(); index++) {
            if (index > 0) {
                // "The first track automatically gets a pre-gap of at least 150 sectors."
                arguments.add("sao_pregap=" + CdAudio.GAP_SECTORS);
            }
            arguments.add(wavFiles.get(index).toString());
        }
        return List.copyOf(arguments);
    }

    static List<String> tableOfContents(DriveId drive) {
        return List.of("dev=" + drive.value(), "-toc");
    }

    /**
     * Bytes written so far, from a pacifier line, given each track's size in bytes; empty for other
     * lines. cdrskin counts megabytes of 1024 × 1024 bytes per track.
     */
    static OptionalLong bytesWritten(String line, List<Long> trackBytes) {
        Matcher matcher = WRITTEN.matcher(line);
        if (!matcher.find()) {
            return OptionalLong.empty();
        }
        int track = Integer.parseInt(matcher.group(1));
        if (track < 1 || track > trackBytes.size()) {
            return OptionalLong.empty();
        }
        long before = trackBytes.subList(0, track - 1).stream()
                .mapToLong(Long::longValue)
                .sum();
        long inTrack = Math.min(Long.parseLong(matcher.group(2)) * MEBIBYTE, trackBytes.get(track - 1));
        return OptionalLong.of(before + inTrack);
    }

    /** The layout from {@code -toc} output, or empty if it lists no tracks or no lead-out. */
    static Optional<TrackLayout> layout(List<String> lines) {
        List<Long> starts = new ArrayList<>();
        Long leadOut = null;
        for (String line : lines) {
            Matcher matcher = TOC_LINE.matcher(line.strip());
            if (matcher.find()) {
                long lba = Long.parseLong(matcher.group(2));
                if (matcher.group(1).equals("lout")) {
                    leadOut = lba;
                } else {
                    starts.add(lba);
                }
            }
        }
        return starts.isEmpty() || leadOut == null ? Optional.empty() : Optional.of(new TrackLayout(starts, leadOut));
    }
}
