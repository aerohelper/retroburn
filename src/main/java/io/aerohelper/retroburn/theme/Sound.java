// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.theme;

/** The sounds every theme has (UX_SPEC section 9). */
public enum Sound {
    SPIN_UP("spin-up.wav"),
    BURNING_LOOP("burning-loop.wav"),
    SUCCESS("success.wav"),
    ERROR("error.wav");

    private final String fileName;

    Sound(String fileName) {
        this.fileName = fileName;
    }

    public String fileName() {
        return fileName;
    }
}
