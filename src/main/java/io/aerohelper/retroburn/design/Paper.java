// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.design;

import java.util.Locale;
import java.util.Set;

/** Paper sizes the designer prints on, portrait, in millimetres. */
public enum Paper {
    A4(210, 297),
    LETTER(215.9, 279.4);

    /** Countries where US Letter is the usual office paper; everywhere else uses A4. */
    private static final Set<String> LETTER_COUNTRIES = Set.of("US", "CA", "MX", "PH");

    private final double widthMm;
    private final double heightMm;

    Paper(double widthMm, double heightMm) {
        this.widthMm = widthMm;
        this.heightMm = heightMm;
    }

    public double widthMm() {
        return widthMm;
    }

    public double heightMm() {
        return heightMm;
    }

    /** The usual paper for an ISO 3166 country code, such as {@code IN} or {@code US}. */
    public static Paper forCountry(String country) {
        return LETTER_COUNTRIES.contains(country.toUpperCase(Locale.ROOT)) ? LETTER : A4;
    }

    /** The saved choice, or the computer's country's paper when nothing valid was saved. */
    public static Paper fromSetting(String saved, Locale locale) {
        for (Paper paper : values()) {
            if (paper.name().equalsIgnoreCase(saved)) {
                return paper;
            }
        }
        return forCountry(locale.getCountry());
    }
}
