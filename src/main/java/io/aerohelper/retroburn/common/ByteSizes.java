// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.common;

import java.util.Locale;

/**
 * Sizes for people: decimal units (1 GB = 1,000,000,000 bytes), as disc capacities are sold ("4.7
 * GB"), with one decimal from MB upwards.
 */
public final class ByteSizes {

    private static final String[] UNITS = {"KB", "MB", "GB", "TB"};

    private ByteSizes() {}

    public static String format(long bytes) {
        if (bytes < 1000) {
            return bytes + " bytes";
        }
        double value = bytes;
        int unit = -1;
        while (value >= 1000 && unit < UNITS.length - 1) {
            value /= 1000;
            unit++;
        }
        return unit == 0
                ? String.format(Locale.ROOT, "%.0f %s", value, UNITS[unit])
                : String.format(Locale.ROOT, "%.1f %s", value, UNITS[unit]);
    }
}
