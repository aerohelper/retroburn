// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.common;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;

/**
 * User-visible text, looked up by key from the {@code i18n/messages} resource bundle.
 *
 * <p>Every string shown to the user goes through this class so the app can be translated later.
 * Instances are immutable and safe to share between threads.
 */
public final class Messages {

    static final String BUNDLE_BASE_NAME = "i18n.messages";

    private final ResourceBundle bundle;

    public Messages(ResourceBundle bundle) {
        this.bundle = Objects.requireNonNull(bundle, "bundle");
    }

    /**
     * Loads the bundled messages for a locale, falling back to English.
     *
     * @throws java.util.MissingResourceException if the bundle is missing from the build
     */
    public static Messages load(Locale locale) {
        return new Messages(ResourceBundle.getBundle(BUNDLE_BASE_NAME, locale));
    }

    /**
     * Returns the text for a key.
     *
     * @throws java.util.MissingResourceException if the key does not exist
     */
    public String get(String key) {
        return bundle.getString(key);
    }

    /**
     * Returns the text for a key with {@code {0}}, {@code {1}}, ... replaced by the arguments.
     *
     * <p>Uses {@link MessageFormat}, so a literal apostrophe in such a message must be written as
     * two apostrophes in the properties file.
     *
     * @throws java.util.MissingResourceException if the key does not exist
     */
    public String format(String key, Object... arguments) {
        return new MessageFormat(get(key), bundle.getLocale()).format(arguments);
    }

    /**
     * Resolves a message created elsewhere, for example inside an error.
     *
     * @throws java.util.MissingResourceException if the key does not exist
     */
    public String format(UserMessage message) {
        return format(message.key(), message.arguments().toArray());
    }
}
