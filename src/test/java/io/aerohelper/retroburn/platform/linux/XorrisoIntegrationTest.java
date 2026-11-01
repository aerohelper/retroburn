// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.linux;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.common.OperationCancelledException;
import io.aerohelper.retroburn.common.RetroBurnException;
import io.aerohelper.retroburn.common.ToolFailedException;
import io.aerohelper.retroburn.engine.BlankMode;
import io.aerohelper.retroburn.engine.BurnEvent;
import io.aerohelper.retroburn.engine.BurnOptions;
import io.aerohelper.retroburn.engine.DiscEntry;
import io.aerohelper.retroburn.engine.DiscTree;
import io.aerohelper.retroburn.engine.DriveId;
import io.aerohelper.retroburn.engine.ImageOptions;
import io.aerohelper.retroburn.engine.MediaState;
import io.aerohelper.retroburn.tools.ExternalTool;
import io.aerohelper.retroburn.tools.PathSearchToolLocator;
import io.aerohelper.retroburn.tools.ProcessRequest;
import io.aerohelper.retroburn.tools.ProcessResult;
import io.aerohelper.retroburn.tools.ProcessRunner;
import io.aerohelper.retroburn.tools.ToolLocator;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Builds real ISO images with the installed GNU xorriso and reads them back; burns, verifies and
 * blanks a file standing in for a drive. Runs on Linux when
 * xorriso is installed (the Linux CI runner installs it).
 */
@EnabledOnOs(OS.LINUX)
class XorrisoIntegrationTest {

    private static final long HUNDRED_MEGABYTES = 100L * 1024 * 1024;

    @TempDir
    Path folder;

    private final ProcessRunner runner = new ProcessRunner();
    private ToolLocator locator;
    private Path xorriso;

    @BeforeEach
    void requireXorriso() {
        PathSearchToolLocator pathSearch = new PathSearchToolLocator(
                PathSearchToolLocator.foldersFromPathVariable(System.getenv("PATH"), File.pathSeparator), List.of(""));
        assumeTrue(pathSearch.locate(ExternalTool.XORRISO).isPresent(), "xorriso is not installed");
        locator = pathSearch;
        xorriso = pathSearch.locate(ExternalTool.XORRISO).orElseThrow();
    }

    @Test
    void imageOfAHundredMegabyteFolderMatchesTheSourceTree() throws IOException {
        Path source = createSourceTree(folder.resolve("source"));
        Path image = folder.resolve("out.iso");
        List<BurnEvent> events = Collections.synchronizedList(new ArrayList<>());

        builder()
                .buildDataImage(
                        DiscTree.of(List.of(
                                new DiscEntry("/Holiday = 2026", source.resolve("holiday")),
                                new DiscEntry("/big.bin", source.resolve("big.bin")),
                                new DiscEntry("/back\\slash.txt", source.resolve("back\\slash.txt")))),
                        new ImageOptions("RETROBURN_TEST"),
                        image,
                        events::add,
                        new CancelToken());

        Path extracted = extract(image);
        Map<String, String> expected = new TreeMap<>(checksums(source.resolve("holiday"), "/Holiday = 2026"));
        expected.put("/big.bin", sha256(source.resolve("big.bin")));
        expected.put("/back\\slash.txt", sha256(source.resolve("back\\slash.txt")));
        assertEquals(expected, checksums(extracted, ""));
        assertTrue(events.contains(BurnEvent.Progress.of(1.0)));
        assertTrue(events.stream().anyMatch(BurnEvent.LogLine.class::isInstance));
        assertTrue(tableOfContents(image).stream().anyMatch(line -> line.contains("Joliet")));
        assertTrue(tableOfContents(image).stream().anyMatch(line -> line.contains("RETROBURN_TEST")));
    }

    @Test
    void replacesAnExistingImageInsteadOfAppendingToIt() throws IOException {
        Path image = folder.resolve("out.iso");
        Path first = Files.writeString(folder.resolve("first.txt"), "first");
        Path second = Files.writeString(folder.resolve("second.txt"), "second");

        build(List.of(new DiscEntry("/first.txt", first)), image);
        build(List.of(new DiscEntry("/second.txt", second)), image);

        assertEquals(Map.of("/second.txt", sha256(second)), checksums(extract(image), ""));
    }

    @Test
    void reportsAMissingSourceAndLeavesNoPartialImage() {
        Path image = folder.resolve("out.iso");

        ToolFailedException error = assertThrows(
                ToolFailedException.class,
                () -> build(List.of(new DiscEntry("/x", folder.resolve("does-not-exist"))), image));

        assertEquals("error.isoCreationFailed", error.userMessage().key());
        assertTrue(error.userMessage().arguments().getFirst().toString().contains("does-not-exist"));
        assertFalse(Files.exists(image));
    }

    @Test
    void cancellingStopsTheBuildAndRemovesThePartialImage() throws IOException {
        Path big = folder.resolve("big.bin");
        writeRandom(big, 400L * 1024 * 1024);
        Path image = folder.resolve("out.iso");
        CancelToken cancel = new CancelToken();

        assertThrows(
                OperationCancelledException.class,
                () -> builder()
                        .buildDataImage(
                                DiscTree.of(List.of(new DiscEntry("/big.bin", big))),
                                new ImageOptions("CANCELLED"),
                                image,
                                event -> {
                                    if (event instanceof BurnEvent.LogLine) {
                                        cancel.cancel();
                                    }
                                },
                                cancel));

        assertFalse(Files.exists(image));
    }

    @Test
    void listingDrivesWorksWithOrWithoutDrives() {
        engine().listDrives().forEach(drive -> assertTrue(drive.id().value().startsWith("/dev/")));
    }

    // A regular file given as "stdio:<path>" is a pseudo-drive that xorriso treats as overwritable
    // media, like a DVD+RW, so writing, reading back and blanking are tested without hardware.

    @Test
    void burnsVerifiesAndBlanksAPseudoDrive() throws IOException {
        Path image = smallImage("disc = 1.iso");
        Path driveFile = folder.resolve("drive.bin");
        DriveId drive = new DriveId("stdio:" + driveFile);
        XorrisoEngine engine = engine();

        List<BurnEvent> burnEvents = Collections.synchronizedList(new ArrayList<>());
        engine.burnImage(drive, image, new BurnOptions(true), burnEvents::add, new CancelToken());
        printForCapture("burn-image", burnEvents);
        assertEquals(BurnEvent.Progress.of(1.0), burnEvents.getLast());
        assertArrayEquals(Files.readAllBytes(image), prefix(driveFile, Files.size(image)));
        assertEquals(MediaState.APPENDABLE, engine.readMedia(drive).state());

        List<BurnEvent> verifyEvents = Collections.synchronizedList(new ArrayList<>());
        engine.verifyImage(drive, image, verifyEvents::add, new CancelToken());
        printForCapture("verify-image", verifyEvents);
        assertEquals(BurnEvent.Progress.of(1.0), verifyEvents.getLast());

        List<BurnEvent> blankEvents = Collections.synchronizedList(new ArrayList<>());
        engine.blank(drive, BlankMode.QUICK, blankEvents::add, new CancelToken());
        printForCapture("blank", blankEvents);
        assertEquals(MediaState.BLANK, engine.readMedia(drive).state());
        RetroBurnException mismatch = assertThrows(
                RetroBurnException.class, () -> engine.verifyImage(drive, image, event -> {}, new CancelToken()));
        assertEquals("error.verifyMismatch", mismatch.userMessage().key());

        // Already blank: a full blank has nothing to do.
        engine.blank(drive, BlankMode.FULL, event -> {}, new CancelToken());
        engine.eject(drive);
    }

    @Test
    void verifyNamesTheFirstDifferentByte() throws IOException {
        Path image = smallImage("disc.iso");
        Path driveFile = folder.resolve("drive.bin");
        DriveId drive = new DriveId("stdio:" + driveFile);
        XorrisoEngine engine = engine();
        engine.burnImage(drive, image, new BurnOptions(true), event -> {}, new CancelToken());
        try (FileChannel channel = FileChannel.open(driveFile, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            ByteBuffer one = ByteBuffer.allocate(1);
            channel.read(one, 1_000_000);
            one.flip();
            one.put(0, (byte) (one.get(0) ^ 0xFF));
            channel.write(one, 1_000_000);
        }

        RetroBurnException error = assertThrows(
                RetroBurnException.class, () -> engine.verifyImage(drive, image, event -> {}, new CancelToken()));

        assertEquals("error.verifyMismatch", error.userMessage().key());
        assertTrue(error.technicalDetail().contains("byte 1000000"), error.technicalDetail());
    }

    @Test
    void cancellingABurnStopsXorriso() throws IOException {
        Path image = smallImage("disc.iso");
        DriveId drive = new DriveId("stdio:" + folder.resolve("drive.bin"));
        CancelToken cancel = new CancelToken();
        cancel.cancel();

        assertThrows(
                OperationCancelledException.class,
                () -> engine().burnImage(drive, image, new BurnOptions(true), event -> {}, cancel));
    }

    /** Prints what an operation reported, so CI logs give real output for the fixtures. */
    private static void printForCapture(String operation, List<BurnEvent> events) {
        synchronized (events) {
            events.forEach(event -> System.out.println("[xorriso " + operation + "] " + event));
        }
    }

    private Path smallImage(String name) throws IOException {
        Path file = folder.resolve("payload.bin");
        writeRandom(file, 20L * 1024 * 1024);
        Path image = folder.resolve(name);
        build(List.of(new DiscEntry("/payload.bin", file)), image);
        return image;
    }

    private static byte[] prefix(Path file, long length) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            return input.readNBytes((int) length);
        }
    }

    private XorrisoEngine engine() {
        return new XorrisoEngine(runner, locator, new SysfsOpticalDrives(Path.of("/sys/block")));
    }

    private void build(List<DiscEntry> entries, Path image) {
        builder().buildDataImage(DiscTree.of(entries), new ImageOptions("TEST"), image, event -> {}, new CancelToken());
    }

    private XorrisoImageBuilder builder() {
        return new XorrisoImageBuilder(runner, locator, folder.resolve("scratch"));
    }

    /** About 100 MB with awkward names: spaces, '=', a backslash, accents and Devanagari. */
    private static Path createSourceTree(Path root) throws IOException {
        Path holiday = Files.createDirectories(root.resolve("holiday/sub dir/deeper"));
        Files.writeString(root.resolve("holiday/a=b.txt"), "equals");
        Files.writeString(holiday.resolve("café हिंदी.txt"), "unicode");
        Files.writeString(root.resolve("holiday/sub dir/notes.txt"), "notes");
        Files.writeString(root.resolve("back\\slash.txt"), "backslash");
        writeRandom(root.resolve("big.bin"), HUNDRED_MEGABYTES);
        return root;
    }

    private static void writeRandom(Path file, long size) throws IOException {
        Random random = new Random(42);
        byte[] buffer = new byte[1 << 20];
        try (OutputStream output = Files.newOutputStream(file)) {
            for (long written = 0; written < size; written += buffer.length) {
                random.nextBytes(buffer);
                output.write(buffer, 0, (int) Math.min(buffer.length, size - written));
            }
        }
    }

    private Path extract(Path image) {
        Path target = folder.resolve("extracted-" + System.nanoTime());
        // "-osirrox on" allows copying files out of the image; "-extract / target" copies everything.
        ProcessResult result = runner.run(
                ProcessRequest.builder(
                                xorriso,
                                List.of(
                                        "-osirrox",
                                        "on",
                                        "-indev",
                                        "stdio:" + image,
                                        "-extract",
                                        "/",
                                        target.toString()))
                        .build(),
                new CancelToken());
        assertTrue(result.succeeded(), result.technicalDetail());
        return target;
    }

    private List<String> tableOfContents(Path image) {
        List<String> lines = Collections.synchronizedList(new ArrayList<>());
        runner.run(
                ProcessRequest.builder(xorriso, List.of("-indev", "stdio:" + image, "-toc"))
                        .onStdoutLine(lines::add)
                        .onStderrLine(lines::add)
                        .build(),
                new CancelToken());
        return lines;
    }

    /** Disc path to SHA-256 of every file under {@code root}, with {@code prefix} prepended. */
    private static Map<String, String> checksums(Path root, String prefix) throws IOException {
        Map<String, String> sums = new TreeMap<>();
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(Files::isRegularFile).forEach(file -> {
                String relative = root.relativize(file).toString().replace(File.separatorChar, '/');
                sums.put(prefix + "/" + relative, sha256(file));
            });
        }
        return sums;
    }

    private static String sha256(Path file) {
        try (InputStream input =
                new DigestInputStream(Files.newInputStream(file), MessageDigest.getInstance("SHA-256"))) {
            input.transferTo(OutputStream.nullOutputStream());
            return HexFormat.of()
                    .formatHex(((DigestInputStream) input).getMessageDigest().digest());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
