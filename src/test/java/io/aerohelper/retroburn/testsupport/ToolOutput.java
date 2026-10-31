// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.testsupport;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Loads captured tool output from {@code src/test/resources/tool-output/}. */
public final class ToolOutput {

    private ToolOutput() {}

    /** The lines of a capture, for example {@code lines("xorriso/version.txt")}. */
    public static List<String> lines(String relativePath) {
        String resource = "/tool-output/" + relativePath;
        try (InputStream input = ToolOutput.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalArgumentException("No fixture " + resource);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
