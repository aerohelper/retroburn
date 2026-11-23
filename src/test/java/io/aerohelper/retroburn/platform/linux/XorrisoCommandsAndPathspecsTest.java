// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.linux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.aerohelper.retroburn.common.UnsupportedInputException;
import io.aerohelper.retroburn.engine.DiscEntry;
import io.aerohelper.retroburn.engine.DiscTree;
import io.aerohelper.retroburn.engine.DriveId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class XorrisoCommandsAndPathspecsTest {

    private static final List<String> PREFIX =
            List.of("-abort_on", "FAILURE", "-return_with", "SORRY", "32", "-report_about", "UPDATE");

    @TempDir
    Path folder;

    @Test
    void buildsTheVerifiedCommandLines() {
        assertEquals(concat(PREFIX, List.of("-devices")), XorrisoCommandBuilder.listDrives());
        assertEquals(
                concat(PREFIX, List.of("-outdev", "/dev/sr0", "-toc")),
                XorrisoCommandBuilder.readMedia(new DriveId("/dev/sr0")));

        Path target = folder.resolve("out.iso");
        Path list = folder.resolve("list.txt");
        assertEquals(
                concat(
                        PREFIX,
                        List.of(
                                "-local_charset", "UTF-8",
                                "-outdev", "stdio:" + target,
                                "-volid", "MY_DISC",
                                "-joliet", "on",
                                "-pathspecs", "on",
                                "-path_list", list.toString(),
                                "-commit", "-end")),
                XorrisoCommandBuilder.createImage(target, "MY_DISC", list));
    }

    @Test
    void escapesEqualsSignsOnlyInTheDiscPath() {
        Path source = folder.resolve("a=b.txt");

        assertEquals(
                "/eq\\=dir/a\\=b.txt=" + source.toAbsolutePath(),
                PathspecListWriter.line(new DiscEntry("/eq=dir/a=b.txt", source)));
    }

    @Test
    void leavesBackslashesAndSpacesAlone() {
        Path source = folder.resolve("x");

        assertEquals(
                "/back\\slash and space.txt=" + source.toAbsolutePath(),
                PathspecListWriter.line(new DiscEntry("/back\\slash and space.txt", source)));
    }

    @Test
    void rejectsDiscNamesXorrisoCannotExpress() {
        Path source = folder.resolve("x");

        assertThrows(UnsupportedInputException.class, () -> PathspecListWriter.line(new DiscEntry("/ends\\", source)));
        assertThrows(UnsupportedInputException.class, () -> PathspecListWriter.line(new DiscEntry("/a\\=b", source)));
    }

    @Test
    void writesOneUtf8LinePerEntry() throws IOException {
        Path list = folder.resolve("list.txt");
        DiscTree tree = DiscTree.of(List.of(
                new DiscEntry("/Café", folder.resolve("café")), new DiscEntry("/हिंदी.txt", folder.resolve("h.txt"))));

        long count = PathspecListWriter.write(tree, list);

        assertEquals(2, count);
        List<String> lines = Files.readAllLines(list, StandardCharsets.UTF_8);
        assertEquals("/Café=" + folder.resolve("café").toAbsolutePath(), lines.get(0));
        assertEquals("/हिंदी.txt=" + folder.resolve("h.txt").toAbsolutePath(), lines.get(1));
    }

    private static List<String> concat(List<String> first, List<String> second) {
        return java.util.stream.Stream.concat(first.stream(), second.stream()).toList();
    }
}
