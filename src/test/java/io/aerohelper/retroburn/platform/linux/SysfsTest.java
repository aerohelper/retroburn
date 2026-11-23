// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.linux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.aerohelper.retroburn.engine.ConnectionType;
import io.aerohelper.retroburn.platform.PowerStatus.PowerSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Uses fake sysfs trees laid out like the kernel's, with ":" replaced by "-" so Windows can create them. */
class SysfsTest {

    @TempDir
    Path root;

    @Test
    void listsOnlyOpticalDrivesSorted() throws IOException {
        Path block = Files.createDirectories(root.resolve("block"));
        for (String name : List.of("sda", "sr1", "nvme0n1", "sr0", "loop0")) {
            Files.createDirectory(block.resolve(name));
        }

        assertEquals(List.of("sr0", "sr1"), new SysfsOpticalDrives(block).deviceNames());
        assertEquals(List.of(), new SysfsOpticalDrives(root.resolve("missing")).deviceNames());
        assertEquals("/dev/sr0", SysfsOpticalDrives.devicePath("sr0"));
    }

    @Test
    void recognisesUsbDrivesFromTheirDevicePath() throws IOException {
        Path block = Files.createDirectories(root.resolve("block"));
        Path usbDevice = Files.createDirectories(
                root.resolve("devices/pci0000-00/0000-00-14.0/usb2/2-1/2-1-1.0/host6/target6-0-0/6-0-0-0/block/sr0"));
        Path sataDevice = Files.createDirectories(
                root.resolve("devices/pci0000-00/0000-00-17.0/ata2/host1/target1-0-0/block/sr1"));
        assumeTrue(symlink(block.resolve("sr0"), usbDevice), "symbolic links are not available");
        symlink(block.resolve("sr1"), sataDevice);

        SysfsOpticalDrives drives = new SysfsOpticalDrives(block);

        assertEquals(ConnectionType.USB, drives.connectionOf("sr0"));
        assertEquals(ConnectionType.OTHER, drives.connectionOf("sr1"));
        assertEquals(ConnectionType.UNKNOWN, drives.connectionOf("sr9"));
    }

    @Test
    void mainsPowerWinsOverADischargingBattery() throws IOException {
        supply("AC", "Mains", "online", "1");
        supply("BAT0", "Battery", "status", "Discharging");

        assertEquals(PowerSource.MAINS, power().current());
    }

    @Test
    void aDischargingSystemBatteryMeansBatteryPower() throws IOException {
        supply("AC", "Mains", "online", "0");
        supply("BAT0", "Battery", "status", "Discharging");

        assertEquals(PowerSource.BATTERY, power().current());
    }

    @Test
    void ignoresPeripheralBatteries() throws IOException {
        Path mouse = supply("hidpp_battery_0", "Battery", "status", "Discharging");
        Files.writeString(mouse.resolve("scope"), "Device\n");

        assertEquals(PowerSource.UNKNOWN, power().current());
    }

    @Test
    void reportsUnknownWithoutAnyPowerInformation() {
        assertEquals(PowerSource.UNKNOWN, new SysfsPowerStatus(root.resolve("absent")).current());
    }

    private SysfsPowerStatus power() {
        return new SysfsPowerStatus(root.resolve("power_supply"));
    }

    private Path supply(String name, String type, String attribute, String value) throws IOException {
        Path supply = Files.createDirectories(root.resolve("power_supply").resolve(name));
        Files.writeString(supply.resolve("type"), type + "\n");
        Files.writeString(supply.resolve(attribute), value + "\n");
        return supply;
    }

    private static boolean symlink(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
            return true;
        } catch (IOException | UnsupportedOperationException e) {
            return false;
        }
    }
}
