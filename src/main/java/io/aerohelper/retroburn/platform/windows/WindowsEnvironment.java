// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.windows;

import java.util.Map;
import java.util.Optional;

/**
 * Reads Windows environment variables, whose names ignore case. Java's {@link System#getenv()} map
 * does not: it holds {@code Path} rather than {@code PATH}, and processes started by some tools see
 * every name in capitals.
 */
final class WindowsEnvironment {

    private WindowsEnvironment() {}

    /** The variable's value, if set and not blank. */
    static Optional<String> get(Map<String, String> environment, String name) {
        String exact = environment.get(name);
        if (exact != null) {
            return Optional.of(exact).filter(value -> !value.isBlank());
        }
        return environment.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }
}
