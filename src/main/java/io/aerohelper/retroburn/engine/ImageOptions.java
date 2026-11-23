// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.engine;

import java.util.Objects;

/**
 * How to build a disc image.
 *
 * @param volumeLabel the name the operating system shows for the disc
 */
public record ImageOptions(String volumeLabel) {

    public ImageOptions {
        Objects.requireNonNull(volumeLabel, "volumeLabel");
        if (volumeLabel.isBlank()) {
            throw new IllegalArgumentException("Volume label must not be blank");
        }
    }
}
