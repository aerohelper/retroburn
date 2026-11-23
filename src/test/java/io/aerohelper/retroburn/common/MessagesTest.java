// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Locale;
import java.util.MissingResourceException;
import org.junit.jupiter.api.Test;

class MessagesTest {

    private final Messages messages = Messages.load(Locale.ENGLISH);

    @Test
    void looksUpTextByKey() {
        assertEquals("What do you want to make?", messages.get("home.heading"));
    }

    @Test
    void fillsInPlaceholders() {
        assertEquals("About RetroBurn", messages.format("about.title", "RetroBurn"));
    }

    @Test
    void fallsBackToEnglishForOtherLocales() {
        assertEquals("Open-source licenses", Messages.load(Locale.JAPANESE).get("licenses.title"));
    }

    @Test
    void failsLoudlyForUnknownKeys() {
        assertThrows(MissingResourceException.class, () -> messages.get("no.such.key"));
    }
}
