// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Properties;
import org.junit.jupiter.api.Test;

class AppInfoTest {

    @Test
    void bundledInfoCarriesTheBuildVersion() {
        AppInfo info = AppInfo.loadBundled();

        assertEquals("RetroBurn", info.name());
        assertFalse(info.version().contains("${"), "Maven resource filtering did not run");
    }

    @Test
    void readsAllFields() {
        AppInfo info = AppInfo.fromProperties(properties("1.2.3"));

        assertEquals(new AppInfo("RetroBurn", "1.2.3", "Copyright", "https://example.org/"), info);
    }

    @Test
    void rejectsMissingOrBlankValues() {
        assertThrows(IllegalStateException.class, () -> AppInfo.fromProperties(properties(" ")));
        assertThrows(IllegalStateException.class, () -> AppInfo.fromProperties(new Properties()));
    }

    private static Properties properties(String version) {
        Properties properties = new Properties();
        properties.setProperty("app.name", "RetroBurn");
        properties.setProperty("app.version", version);
        properties.setProperty("app.copyright", "Copyright");
        properties.setProperty("app.sourceUrl", "https://example.org/");
        return properties;
    }
}
