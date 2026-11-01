// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.windows;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HelperFilesTest {

    @TempDir
    Path root;

    @Test
    void copiesBothFilesIntoAFolderNamedAfterTheirHash() throws IOException {
        Path script = new HelperFiles(root).script();

        assertEquals(HelperFiles.SCRIPT, script.getFileName().toString());
        assertEquals(root, script.getParent().getParent());
        assertTrue(script.getParent().getFileName().toString().matches("[0-9a-f]{16}"));
        assertArrayEquals(bundled(HelperFiles.SCRIPT), Files.readAllBytes(script));
        assertArrayEquals(bundled(HelperFiles.SOURCE), Files.readAllBytes(script.resolveSibling(HelperFiles.SOURCE)));
    }

    @Test
    void repairsADamagedCopy() throws IOException {
        HelperFiles files = new HelperFiles(root);
        Path source = files.script().resolveSibling(HelperFiles.SOURCE);
        Files.writeString(source, "// damaged");

        files.script();

        assertArrayEquals(bundled(HelperFiles.SOURCE), Files.readAllBytes(source));
    }

    @Test
    void theScriptIsTheSameFolderEveryTime() {
        assertEquals(new HelperFiles(root).script(), new HelperFiles(root).script());
    }

    private static byte[] bundled(String name) throws IOException {
        try (InputStream input = HelperFiles.class.getResourceAsStream("/windows-helper/" + name)) {
            return input.readAllBytes();
        }
    }
}
