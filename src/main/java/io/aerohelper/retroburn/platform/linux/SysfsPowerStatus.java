// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.linux;

import io.aerohelper.retroburn.platform.PowerStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Battery or mains power, from {@code /sys/class/power_supply}.
 *
 * <p>Attributes are documented in the kernel's {@code Documentation/ABI/testing/sysfs-class-power}:
 * {@code type} is Battery, UPS, Mains, USB or Wireless; {@code online} is 0 when a supply is
 * offline; a battery's {@code status} is Unknown, Charging, Discharging, Not charging or Full; its
 * {@code scope} is Device for peripherals (a wireless mouse) rather than the computer.
 */
final class SysfsPowerStatus implements PowerStatus {

    private final Path powerSupplyRoot;

    /** @param powerSupplyRoot normally {@code /sys/class/power_supply}; tests pass a fake tree */
    SysfsPowerStatus(Path powerSupplyRoot) {
        this.powerSupplyRoot = powerSupplyRoot;
    }

    @Override
    public PowerSource current() {
        List<Path> supplies = supplies();
        boolean mainsOnline = supplies.stream()
                .anyMatch(supply -> "Mains".equals(read(supply, "type")) && isOnline(read(supply, "online")));
        if (mainsOnline) {
            return PowerSource.MAINS;
        }
        boolean systemBatteryDischarging = supplies.stream()
                .filter(supply -> "Battery".equals(read(supply, "type")))
                .filter(supply -> !"Device".equals(read(supply, "scope")))
                .anyMatch(supply -> "Discharging".equals(read(supply, "status")));
        return systemBatteryDischarging ? PowerSource.BATTERY : PowerSource.UNKNOWN;
    }

    private static boolean isOnline(String value) {
        return value != null && !value.equals("0");
    }

    private List<Path> supplies() {
        if (!Files.isDirectory(powerSupplyRoot)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(powerSupplyRoot)) {
            return entries.toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** An attribute's value without the trailing newline, or null if it is missing or unreadable. */
    private static String read(Path supply, String attribute) {
        try {
            return Files.readString(supply.resolve(attribute), StandardCharsets.US_ASCII)
                    .strip();
        } catch (IOException e) {
            return null;
        }
    }
}
