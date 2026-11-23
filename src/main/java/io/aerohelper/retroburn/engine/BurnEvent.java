// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.engine;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** Something an engine reports while it works. */
public sealed interface BurnEvent {

    /**
     * Progress of the current operation.
     *
     * @param fraction 0.0 to 1.0
     * @param speed write speed as shown by the tool, for example "8.0x DVD", if known
     * @param bufferFillPercent the drive's buffer fill level, if known
     */
    record Progress(double fraction, Optional<String> speed, OptionalInt bufferFillPercent) implements BurnEvent {

        public Progress {
            if (Double.isNaN(fraction) || fraction < 0.0 || fraction > 1.0) {
                throw new IllegalArgumentException("Fraction must be between 0 and 1: " + fraction);
            }
            Objects.requireNonNull(speed, "speed");
            Objects.requireNonNull(bufferFillPercent, "bufferFillPercent");
        }

        public static Progress of(double fraction) {
            return new Progress(fraction, Optional.empty(), OptionalInt.empty());
        }
    }

    /** One line of raw tool output, for the "Show details" log. */
    record LogLine(String text) implements BurnEvent {

        public LogLine {
            Objects.requireNonNull(text, "text");
        }
    }
}
