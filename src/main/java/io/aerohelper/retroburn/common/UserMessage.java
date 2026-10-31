// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.common;

import java.util.List;
import java.util.Objects;

/**
 * Text for the user, as a message key plus arguments, resolved later through {@link Messages}.
 *
 * <p>Errors and availability reasons are created deep inside the app where no locale is known;
 * carrying the key instead of finished text keeps them translatable.
 *
 * @param key message key in {@code i18n/messages.properties}
 * @param arguments values for the {@code {0}}, {@code {1}}, ... placeholders
 */
public record UserMessage(String key, List<Object> arguments) {

    public UserMessage {
        Objects.requireNonNull(key, "key");
        arguments = List.copyOf(arguments);
    }

    public static UserMessage of(String key, Object... arguments) {
        return new UserMessage(key, List.of(arguments));
    }
}
