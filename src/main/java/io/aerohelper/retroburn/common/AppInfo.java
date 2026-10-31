// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Facts about this build of RetroBurn, shown on the About screen.
 *
 * @param name application name
 * @param version version from the Maven build, for example "0.1.0"
 * @param copyright copyright line
 * @param sourceUrl where the source code is published
 */
public record AppInfo(String name, String version, String copyright, String sourceUrl) {

    static final String RESOURCE = "/app.properties";

    /**
     * Reads the {@code app.properties} resource, which Maven fills in at build time.
     *
     * @throws IllegalStateException if the resource or one of its keys is missing
     */
    public static AppInfo loadBundled() {
        try (InputStream input = AppInfo.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing bundled resource " + RESOURCE);
            }
            Properties properties = new Properties();
            properties.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            return fromProperties(properties);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + RESOURCE, e);
        }
    }

    static AppInfo fromProperties(Properties properties) {
        return new AppInfo(
                required(properties, "app.name"),
                required(properties, "app.version"),
                required(properties, "app.copyright"),
                required(properties, "app.sourceUrl"));
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing '" + key + "' in " + RESOURCE);
        }
        return value;
    }
}
