// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.linux;

import io.aerohelper.retroburn.engine.ConnectionType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Optical drives as the kernel lists them in {@code /sys/block}, read without touching the drives.
 *
 * <p>SCSI CD/DVD/BD drives (including USB ones) appear as {@code sr0}, {@code sr1}, ...; their
 * device files are {@code /dev/srN}.
 */
final class SysfsOpticalDrives {

    private static final Pattern USB_BUS_SEGMENT = Pattern.compile("usb\\d+");

    private final Path sysBlock;

    /** @param sysBlock normally {@code /sys/block}; tests pass a fake tree */
    SysfsOpticalDrives(Path sysBlock) {
        this.sysBlock = sysBlock;
    }

    /** Names such as {@code sr0}, sorted; empty when there are none or sysfs is unreadable. */
    List<String> deviceNames() {
        if (!Files.isDirectory(sysBlock)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(sysBlock)) {
            return entries.map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("sr"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    static String devicePath(String deviceName) {
        return "/dev/" + deviceName;
    }

    /**
     * Whether a drive is on USB. {@code /sys/block/srN} links into {@code /sys/devices/...}, which
     * mirrors the bus topology; USB devices sit below a {@code usbN} bus folder.
     */
    ConnectionType connectionOf(String deviceName) {
        try {
            Path real = sysBlock.resolve(deviceName).toRealPath();
            for (Path segment : real) {
                if (USB_BUS_SEGMENT.matcher(segment.toString()).matches()) {
                    return ConnectionType.USB;
                }
            }
            return ConnectionType.OTHER;
        } catch (IOException e) {
            return ConnectionType.UNKNOWN;
        }
    }
}
