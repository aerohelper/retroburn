// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.engine;

import java.util.Objects;

/**
 * Address of an optical drive, meaningful only to the platform that produced it: a device path such
 * as {@code /dev/sr0} on Linux, the IMAPI2 unique id on Windows.
 */
public record DriveId(String value) {

    public DriveId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Drive id must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
