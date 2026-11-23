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
 */
final class XorrisoProgressParser {

    private static final Pattern WRITING = Pattern.compile(
            "UPDATE : Writing:\\s+\\d+s\\s+([\\d.]+)%(?:\\s+fifo\\s+\\d+%\\s+buf\\s+(\\d+)%)?(?:\\s+([\\d.]+)x([CDB]))?");

    private XorrisoProgressParser() {}

    /** The progress in a line, or empty if the line is not a progress message. */
    static Optional<BurnEvent.Progress> parse(String line) {
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
