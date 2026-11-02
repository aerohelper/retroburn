// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.video;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The order of files in a VIDEO_TS folder as DVD players expect them on the disc: {@code
 * VIDEO_TS.IFO}, {@code VIDEO_TS.VOB}, {@code VIDEO_TS.BUP}, then for each titleset its IFO, menu VOB,
 * title VOBs and BUP. See {@code docs/design/LLD-video-dvd.md} section 6.
 */
public final class VideoTsOrder {

    private static final Pattern VTS = Pattern.compile("VTS_(\\d{2})_(\\d)\\.(IFO|VOB|BUP)");

    private VideoTsOrder() {}

    /** {@code names} sorted into DVD order; names this rule does not know go last, by name. */
    public static List<String> sort(List<String> names) {
        return names.stream().sorted(Comparator.comparing(VideoTsOrder::key)).toList();
    }

    private static String key(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        switch (upper) {
            case "VIDEO_TS.IFO" -> {
                return "0";
            }
            case "VIDEO_TS.VOB" -> {
                return "1";
            }
            case "VIDEO_TS.BUP" -> {
                return "2";
            }
            default -> {}
        }
        Matcher matcher = VTS.matcher(upper);
        if (matcher.matches()) {
            int part = switch (matcher.group(3)) {
                case "IFO" -> 0;
                case "VOB" -> 1 + Integer.parseInt(matcher.group(2));
                default -> 20;
            };
            return "3" + matcher.group(1) + "%02d".formatted(part);
        }
        return "9" + upper;
    }
}
