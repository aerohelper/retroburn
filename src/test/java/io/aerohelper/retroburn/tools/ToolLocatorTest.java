// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ToolLocatorTest {

    @TempDir
    Path folder;

    @Test
    void findsAnExecutableInTheFirstFolderThatHasIt() throws IOException {
        Path first = Files.createDirectory(folder.resolve("first"));
        Path second = Files.createDirectory(folder.resolve("second"));
        Path expected = executable(second.resolve("xorriso"));

        PathSearchToolLocator locator = new PathSearchToolLocator(List.of(first, second), List.of(""));

        assertEquals(Optional.of(expected.toAbsolutePath()), locator.locate(ExternalTool.XORRISO));
    }

    @Test
    void triesEachExtensionInOrder() throws IOException {
        Path expected = executable(folder.resolve("ffmpeg.exe"));

        PathSearchToolLocator locator = new PathSearchToolLocator(List.of(folder), List.of("", ".exe"));

        assertEquals(Optional.of(expected.toAbsolutePath()), locator.locate(ExternalTool.FFMPEG));
    }

    @Test
    void reportsMissingTools() {
        PathSearchToolLocator locator = new PathSearchToolLocator(List.of(folder), List.of(""));

        assertTrue(locator.locate(ExternalTool.DVDAUTHOR).isEmpty());
    }

    @Test
    void ignoresFoldersWithTheToolsName() throws IOException {
        Files.createDirectory(folder.resolve("spumux"));

        assertTrue(new PathSearchToolLocator(List.of(folder), List.of(""))
                .locate(ExternalTool.SPUMUX)
                .isEmpty());
    }

    @Test
    void splitsPathVariablesAndSkipsEmptyEntries() {
        List<Path> folders = PathSearchToolLocator.foldersFromPathVariable("a;;b; ", ";");

        assertEquals(List.of(Path.of("a"), Path.of("b")), folders);
        assertTrue(PathSearchToolLocator.foldersFromPathVariable(null, ":").isEmpty());
    }

    @Test
    void prefersTheUsersPathAndSeesSettingChangesImmediately() throws IOException {
        Path custom = executable(folder.resolve("my-xorriso"));
        Path normal = folder.resolve("normal");
        ToolLocator fallback = tool -> Optional.of(normal);
        Map<ExternalTool, Path> settings = new HashMap<>();
        OverridingToolLocator locator = new OverridingToolLocator(() -> Map.copyOf(settings), fallback);

        assertEquals(Optional.of(normal), locator.locate(ExternalTool.XORRISO));
        settings.put(ExternalTool.XORRISO, custom);
        assertEquals(Optional.of(custom.toAbsolutePath()), locator.locate(ExternalTool.XORRISO));
    }

    @Test
    void fallsBackWhenTheUsersPathNoLongerExists() {
        Path normal = folder.resolve("normal");
        OverridingToolLocator locator = new OverridingToolLocator(
                () -> Map.of(ExternalTool.XORRISO, folder.resolve("deleted")), tool -> Optional.of(normal));

        assertEquals(Optional.of(normal), locator.locate(ExternalTool.XORRISO));
    }

    private static Path executable(Path file) throws IOException {
        Files.writeString(file, "");
        file.toFile().setExecutable(true);
        return file;
    }
}
