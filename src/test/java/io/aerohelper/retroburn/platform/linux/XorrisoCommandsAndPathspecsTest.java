// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.linux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aerohelper.retroburn.common.UnsupportedInputException;
import io.aerohelper.retroburn.engine.BurnEvent;
import io.aerohelper.retroburn.engine.DiscEntry;
import io.aerohelper.retroburn.engine.DiscTree;
import io.aerohelper.retroburn.engine.DriveId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
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
    void buildsTheWritingCommandLinesFromTheManual() {
        DriveId drive = new DriveId("/dev/sr0");
        Path image = folder.resolve("disc.iso");

        assertEquals(
                concat(
                        PREFIX,
                        List.of(
                                "-as",
                                "cdrecord",
                                "-v",
                                "dev=/dev/sr0",
                                image.toAbsolutePath().toString(),
                                "--")),
                XorrisoCommandBuilder.burnImage(drive, image, true));
        assertEquals(
                concat(
                        PREFIX,
                        List.of(
                                "-as",
                                "cdrecord",
                                "-v",
                                "dev=/dev/sr0",
                                "-multi",
                                image.toAbsolutePath().toString(),
                                "--")),
                XorrisoCommandBuilder.burnImage(drive, image, false));
        assertEquals(
                concat(PREFIX, List.of("-outdev", "/dev/sr0", "-blank", "as_needed")),
                XorrisoCommandBuilder.blank(drive, false));
        assertEquals(
                concat(PREFIX, List.of("-outdev", "/dev/sr0", "-blank", "all")),
                XorrisoCommandBuilder.blank(drive, true));
        assertEquals(
                concat(
                        PREFIX,
                        List.of(
                                "-outdev",
                                "/dev/sr0",
                                "-check_media",
                                "use=outdev",
                                "what=disc",
                                "min_lba=0",
                                "max_lba=15967",
                                "event=FAILURE",
                                "data_to=-",
                                "--")),
                XorrisoCommandBuilder.readBack(drive, 15_968));
        assertEquals(
                concat(PREFIX, List.of("-outdev", "/dev/sr0", "-eject", "out")), XorrisoCommandBuilder.eject(drive));
        assertThrows(IllegalArgumentException.class, () -> XorrisoCommandBuilder.readBack(drive, 0));
    }

    @Test
    void comparesReadBackBytesWithTheImageAsTheyArrive() throws IOException {
        byte[] content = new byte[10_000];
        new Random(7).nextBytes(content);
        Path image = Files.write(folder.resolve("image.iso"), content);
        List<BurnEvent> events = new ArrayList<>();
        AtomicInteger mismatches = new AtomicInteger();

        try (ImageComparison same = new ImageComparison(image, events::add, mismatches::incrementAndGet)) {
            same.write(content, 0, 4000);
            same.write(content, 4000, 6000);
            // The rest of the last 2048-byte block is ignored.
            same.write(new byte[2288]);
            assertTrue(same.complete());
            // Under one 4 MiB progress step, and completion is the engine's to report.
            assertTrue(events.isEmpty(), events.toString());
        }

        byte[] damaged = content.clone();
        damaged[7777] ^= 1;
        try (ImageComparison different = new ImageComparison(image, events::add, mismatches::incrementAndGet)) {
            different.write(damaged, 0, 5000);
            different.write(damaged, 5000, 5000);
            different.write(damaged, 0, 10);
            assertEquals(OptionalLong.of(7777), different.mismatch());
            assertFalse(different.complete());
        }
        assertEquals(1, mismatches.get());

        try (ImageComparison shorter = new ImageComparison(image, events::add, mismatches::incrementAndGet)) {
            shorter.write(content, 0, 6144);
            assertFalse(shorter.complete());
            assertEquals(6144, shorter.compared());
        }
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
