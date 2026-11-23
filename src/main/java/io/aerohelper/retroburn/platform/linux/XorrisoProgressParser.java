// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.linux;

import io.aerohelper.retroburn.engine.BurnEvent;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses xorriso's write progress messages.
 *
 * <p>Format from {@code Xorriso_pacifier_loop()} in libisoburn's {@code xorriso/write_run.c}:
 * {@code "Writing: %10.fs  %5.1f%% "}, then {@code "  fifo %3d%%  buf %3d%%"}, then — after the first
 * interval — {@code "  %5.1fx%s "} where the unit letter is C, D or B for multiples of 1x CD, DVD or
 * BD speed. Example: {@code xorriso : UPDATE : Writing:  17888s  1.2%  fifo  93%  buf  50%  65.5xD}.
 *
 * <p>Burning through the cdrecord emulation prints the manual's "cdrecord" pacifier format instead
 * (see {@code -pacifier}: "X of Y MB written (fifo nn%) [buf mmm%]"). Captured on a {@code stdio:}
 * pseudo-drive: {@code xorriso : UPDATE :    0 of   20 MB written [buf  50%]}.
 */
final class XorrisoProgressParser {

    private static final Pattern WRITING = Pattern.compile(
            "UPDATE : Writing:\\s+\\d+s\\s+([\\d.]+)%(?:\\s+fifo\\s+\\d+%\\s+buf\\s+(\\d+)%)?(?:\\s+([\\d.]+)x([CDB]))?");

    private static final Pattern MEGABYTES_WRITTEN = Pattern.compile(
            "UPDATE :\\s+(\\d+) of\\s+(\\d+) MB written(?:\\s+\\(fifo\\s+\\d+%\\))?(?:\\s+\\[buf\\s+(\\d+)%\\])?");

    private XorrisoProgressParser() {}

    /** The progress in a line, or empty if the line is not a progress message. */
    static Optional<BurnEvent.Progress> parse(String line) {
        Matcher megabytes = MEGABYTES_WRITTEN.matcher(line);
        if (megabytes.find()) {
            long total = Long.parseLong(megabytes.group(2));
            double fraction =
                    total == 0 ? 0.0 : Math.clamp((double) Long.parseLong(megabytes.group(1)) / total, 0.0, 1.0);
            OptionalInt buffer = megabytes.group(3) == null
                    ? OptionalInt.empty()
                    : OptionalInt.of(Integer.parseInt(megabytes.group(3)));
            return Optional.of(new BurnEvent.Progress(fraction, Optional.empty(), buffer));
        }
        Matcher matcher = WRITING.matcher(line);
        if (!matcher.find()) {
            return Optional.empty();
        }
        double fraction = Math.clamp(Double.parseDouble(matcher.group(1)) / 100.0, 0.0, 1.0);
        OptionalInt buffer =
                matcher.group(2) == null ? OptionalInt.empty() : OptionalInt.of(Integer.parseInt(matcher.group(2)));
        Optional<String> speed = matcher.group(3) == null
                ? Optional.empty()
                : Optional.of(matcher.group(3) + "x " + speedUnit(matcher.group(4)));
        return Optional.of(new BurnEvent.Progress(fraction, speed, buffer));
    }

    private static String speedUnit(String letter) {
        return switch (letter) {
            case "C" -> "CD";
            case "B" -> "BD";
            default -> "DVD";
        };
    }
}
