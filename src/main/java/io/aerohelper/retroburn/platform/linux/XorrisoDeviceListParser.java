// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.linux;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the output of {@code xorriso -devices}.
 *
 * <p>Each drive line is printed by {@code Xorriso_show_devices()} in libisoburn's {@code
 * xorriso/drive_mgt.c} with the format {@code "%d  -dev '%s' " + padding + "%s :  '%-8.8s' '%s' \n"}:
 * index, device path, permission flags, vendor padded to 8 characters, product.
 */
final class XorrisoDeviceListParser {

    private static final Pattern DRIVE_LINE =
            Pattern.compile("^\\s*\\d+\\s+-dev '([^']+)'\\s+\\S+\\s+:\\s+'([^']*)'\\s+'([^']*)'");
    private static final String NO_DRIVES = "No drives found";

    /** A drive as xorriso reports it. */
    record ListedDrive(String address, String vendor, String product) {}

    private XorrisoDeviceListParser() {}

    static List<ListedDrive> parse(List<String> outputLines) {
        List<ListedDrive> drives = new ArrayList<>();
        for (String line : outputLines) {
            Matcher matcher = DRIVE_LINE.matcher(line);
            if (matcher.find()) {
                drives.add(new ListedDrive(
                        matcher.group(1),
                        matcher.group(2).strip(),
                        matcher.group(3).strip()));
            }
        }
        return List.copyOf(drives);
    }

    /** True when xorriso said it found no accessible drive, which is not an error. */
    static boolean reportsNoDrives(List<String> outputLines) {
        return outputLines.stream().anyMatch(line -> line.contains(NO_DRIVES));
    }
}
