// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.engine;

import java.util.Objects;

/**
 * An optical drive the user can choose.
 *
 * @param id platform address of the drive
 * @param vendor manufacturer as reported by the drive, possibly empty
 * @param product model as reported by the drive, possibly empty
 * @param connection how the drive is attached
 * @param accessible false when the drive exists but the user lacks permission to use it
 * @param location where users know the drive from: the drive letter on Windows ({@code D:}), the
 *     device on Linux ({@code /dev/sr0}); empty if there is none
 */
public record DriveInfo(
        DriveId id, String vendor, String product, ConnectionType connection, boolean accessible, String location) {

    public DriveInfo {
        Objects.requireNonNull(id, "id");
        vendor = Objects.requireNonNullElse(vendor, "").strip();
        product = Objects.requireNonNullElse(product, "").strip();
        Objects.requireNonNull(connection, "connection");
        location = Objects.requireNonNullElse(location, "").strip();
    }

    /** A drive with no location to show. */
    public DriveInfo(DriveId id, String vendor, String product, ConnectionType connection, boolean accessible) {
        this(id, vendor, product, connection, accessible, "");
    }

    /** Vendor and product for display, falling back to the drive address. */
    public String displayName() {
        String name = (vendor + " " + product).strip();
        return name.isEmpty() ? id.value() : name;
    }

    /** The display name followed by the location, for example "Slimtype eBAU108 (D:)". */
    public String displayNameWithLocation() {
        return location.isEmpty() || location.equals(displayName())
                ? displayName()
                : displayName() + " (" + location + ")";
    }
}
