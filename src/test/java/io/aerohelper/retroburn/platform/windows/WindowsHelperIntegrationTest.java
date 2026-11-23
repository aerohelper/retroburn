// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.windows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.common.OperationCancelledException;
import io.aerohelper.retroburn.common.RetroBurnException;
import io.aerohelper.retroburn.engine.BurnEvent;
import io.aerohelper.retroburn.engine.DiscEntry;
import io.aerohelper.retroburn.engine.DiscTree;
import io.aerohelper.retroburn.engine.ImageOptions;
import io.aerohelper.retroburn.platform.PowerStatus.PowerSource;
import io.aerohelper.retroburn.project.DataCompilation;
import io.aerohelper.retroburn.project.ImageSizeEstimate;
import io.aerohelper.retroburn.project.TreeSizeScanner;
import io.aerohelper.retroburn.testsupport.IsoImage;
import io.aerohelper.retroburn.testsupport.Mp3PlanFixture;
import io.aerohelper.retroburn.tools.ProcessRequest;
import io.aerohelper.retroburn.tools.ProcessResult;
import io.aerohelper.retroburn.tools.ProcessRunner;
import io.aerohelper.retroburn.video.VideoTsOrder;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs the real Windows helper through PowerShell and IMAPI2. Windows only; drives are optional (CI
 * runners have none), so drive tests accept an empty list.
 */
@EnabledOnOs(OS.WINDOWS)
class WindowsHelperIntegrationTest {

    private static final long HUNDRED_MEGABYTES = 100L * 1024 * 1024;

    @TempDir
    Path folder;

    private final ProcessRunner runner = new ProcessRunner();
    private HelperClient helper;

    @BeforeEach
    void createHelper() {
        HelperFiles files = new HelperFiles(folder.resolve("helper"));
        helper = new HelperClient(
                runner, HelperClient.powerShell(WindowsPlatform.powerShell(System.getenv()), files::script));
    }

    @Test
    void probeReportsAFullLanguagePowerShell() {
        AtomicReference<HelperEvent.Ready> ready = new AtomicReference<>();

        helper.run(
                HelperRequest.probe(),
                event -> {
                    if (event instanceof HelperEvent.Ready found) {
                        ready.set(found);
                    }
                },
                CancelToken.none(),
                HelperClient.Limits.query());

        assertTrue(
                ready.get().powerShellVersion().startsWith("5.1"), ready.get().toString());
        assertEquals("FullLanguage", ready.get().languageMode());
    }

    @Test
    void listsDrivesAndTheirMedia() {
        Imapi2Engine engine = new Imapi2Engine(helper);

        engine.listDrives().forEach(drive -> {
            assertTrue(drive.id().value().startsWith("\\\\?\\"), drive.id().value());
            if (drive.accessible()) {
                assertNotEquals(null, engine.readMedia(drive.id()));
            }
        });
    }

    @Test
    void powerStatusIsKnown() {
        assertNotEquals(PowerSource.UNKNOWN, new HelperPowerStatus(helper).current());
    }

    @Test
    void keepsTheComputerAwakeUntilReleased() {
        new ExecutionStateSleepInhibitor(helper).acquire("RetroBurn test").close();
    }

    @Test
    void imageOfAHundredMegabyteFolderMatchesTheSourceTree() throws IOException {
        Path source = createSourceTree(folder.resolve("source"));
        Path image = folder.resolve("out.iso");
        List<BurnEvent> events = Collections.synchronizedList(new ArrayList<>());

        new Imapi2ImageBuilder(helper)
                .buildDataImage(
                        DiscTree.of(List.of(
                                new DiscEntry("/Holiday = 2026", source.resolve("holiday")),
                                new DiscEntry("/nested/deeper/big.bin", source.resolve("big.bin")))),
                        new ImageOptions("RETROBURN_TEST"),
                        image,
                        events::add,
                        new CancelToken());

        Map<String, String> expected = new TreeMap<>(checksums(source.resolve("holiday"), "/Holiday = 2026"));
        expected.put("/nested/deeper/big.bin", sha256(source.resolve("big.bin")));
        assertEquals(expected, mountedChecksums(image));
        assertTrue(events.contains(BurnEvent.Progress.of(1.0)));
    }

    @Test
    void replacesAnExistingImage() throws IOException {
        Path image = Files.writeString(folder.resolve("out.iso"), "an old file that is not an image");
        Path file = Files.writeString(folder.resolve("only.txt"), "only");

        new Imapi2ImageBuilder(helper)
                .buildDataImage(
                        DiscTree.of(List.of(new DiscEntry("/only.txt", file))),
                        new ImageOptions("REPLACED"),
                        image,
                        event -> {},
                        new CancelToken());

        assertEquals(Map.of("/only.txt", sha256(file)), mountedChecksums(image));
    }

    @Test
    void reportsAMissingSourceAndLeavesNoPartialImage() {
        Path image = folder.resolve("out.iso");

        RetroBurnException error = assertThrows(
                RetroBurnException.class,
                () -> new Imapi2ImageBuilder(helper)
                        .buildDataImage(
                                DiscTree.of(List.of(new DiscEntry("/x", folder.resolve("does-not-exist")))),
                                new ImageOptions("TEST"),
                                image,
                                event -> {},
                                new CancelToken()));

        assertEquals("error.sourceMissing", error.userMessage().key());
        assertFalse(Files.exists(image));
    }

    @Test
    void cancellingStopsTheBuildAndRemovesThePartialImage() throws IOException {
        Path big = folder.resolve("big.bin");
        writeRandom(big, 400L * 1024 * 1024);
        Path image = folder.resolve("out.iso");
        CancelToken cancel = new CancelToken();
        // Cancelled up front: the helper's input is closed while PowerShell is still starting, so the
        // helper must notice at its first checkpoint. Cancelling on a progress event raced fast disks,
        // which finished the image before the request arrived.
        cancel.cancel();

        assertThrows(
                OperationCancelledException.class,
                () -> new Imapi2ImageBuilder(helper)
                        .buildDataImage(
                                DiscTree.of(List.of(new DiscEntry("/big.bin", big))),
                                new ImageOptions("CANCELLED"),
                                image,
                                event -> {},
                                cancel));

        assertFalse(Files.exists(image));
    }

    @Test
    void theSizeEstimateCoversARealImageOfManySmallFilesAndKeepsTheLabel() throws IOException {
        Path source = Files.createDirectories(folder.resolve("small"));
        for (int i = 0; i < 2000; i++) {
            Files.writeString(source.resolve("note " + i + ".txt"), "note " + i);
        }
        writeRandom(source.resolve("one-megabyte.bin"), 1024 * 1024);
        DataCompilation compilation = DataCompilation.empty().add(List.of(source));
        Path image = folder.resolve("small.iso");

        new Imapi2ImageBuilder(helper)
                .buildDataImage(compilation, new ImageOptions("Holiday 2026"), image, event -> {}, new CancelToken());

        long estimate = ImageSizeEstimate.bytes(new TreeSizeScanner().scan(compilation, new CancelToken()));
        long actual = Files.size(image);
        System.out.println("[estimate] IMAPI2FS image " + actual + " bytes, estimate " + estimate);
        assertTrue(estimate >= actual, "estimate " + estimate + " < image " + actual);
        assertTrue(estimate < actual * 2, "estimate " + estimate + " far above image " + actual);
        assertEquals("Holiday 2026", volumeLabel(image));
    }

    @Test
    void anMp3DiscImageHasCarNamesAndTheMp3sUnchanged() throws IOException {
        Mp3PlanFixture fixture = Mp3PlanFixture.create(folder.resolve("music"));
        assertEquals(
                Mp3PlanFixture.expectedPaths(), List.copyOf(fixture.expected().keySet()));
        Path image = folder.resolve("mp3.iso");

        new Imapi2ImageBuilder(helper)
                .buildDataImage(fixture.tree(), new ImageOptions("MP3 DISC"), image, event -> {}, new CancelToken());

        Map<String, String> expected = new TreeMap<>();
        fixture.expected().forEach((discPath, source) -> expected.put(discPath, sha256(source)));
        assertEquals(expected, mountedChecksums(image));
    }

    @Test
    void aDvdVideoImageHasUdf102NoJolietAndTheVideoTsFilesInDvdOrder() throws IOException {
        Path dvd = folder.resolve("dvd");
        Path videoTs = Files.createDirectories(dvd.resolve("VIDEO_TS"));
        Files.createDirectories(dvd.resolve("AUDIO_TS"));
        // Written in an order unlike the DVD order, with sizes that are not whole sectors.
        List<String> names = List.of(
                "VTS_01_0.BUP",
                "VTS_01_2.VOB",
                "VIDEO_TS.BUP",
                "VTS_01_1.VOB",
                "VTS_01_0.IFO",
                "VIDEO_TS.VOB",
                "VTS_01_0.VOB",
                "VIDEO_TS.IFO");
        for (int i = 0; i < names.size(); i++) {
            writeRandom(videoTs.resolve(names.get(i)), 5000 + i * 7000L);
        }
        Path image = folder.resolve("dvd.iso");

        new Imapi2ImageBuilder(helper).buildDvdVideoImage(dvd, "HOLIDAY", image, event -> {}, new CancelToken());

        try (IsoImage iso = new IsoImage(image)) {
            List<String> descriptors = iso.descriptorIdentifiers();
            System.out.println("[dvd] descriptors " + descriptors);
            assertTrue(descriptors.contains("CD001/1"), descriptors.toString());
            assertTrue(descriptors.contains("NSR02"), "UDF 1.02 descriptor missing: " + descriptors);
            assertFalse(descriptors.contains("CD001/2"), "Joliet must be left out: " + descriptors);
            List<IsoImage.Entry> files = iso.list("/VIDEO_TS").stream()
                    .sorted(Comparator.comparingLong(IsoImage.Entry::lba))
                    .toList();
            files.forEach(entry -> System.out.println("[dvd] " + entry));
            assertEquals(
                    VideoTsOrder.sort(names),
                    files.stream().map(IsoImage.Entry::name).toList());
            assertTrue(iso.list("/").stream().anyMatch(entry -> entry.name().equals("AUDIO_TS")));
        }
    }

    private String volumeLabel(Path image) {
        String quoted = "'" + image.toAbsolutePath().toString().replace("'", "''") + "'";
        try {
            return powerShell("Mount-DiskImage -ImagePath " + quoted + " | Out-Null; "
                    + "for ($i = 0; $i -lt 60; $i++) { "
                    + "$volume = Get-DiskImage -ImagePath " + quoted + " | Get-Volume; "
                    + "if ($volume.DriveLetter) { $volume.FileSystemLabel; break }; Start-Sleep -Milliseconds 500 }");
        } finally {
            powerShell("Dismount-DiskImage -ImagePath " + quoted + " | Out-Null");
        }
    }

    /** About 100 MB with awkward names: spaces, '=', accents and Devanagari. */
    private static Path createSourceTree(Path root) throws IOException {
        Path holiday = Files.createDirectories(root.resolve("holiday/sub dir/deeper"));
        Files.writeString(root.resolve("holiday/a=b.txt"), "equals");
        Files.writeString(holiday.resolve("café हिंदी.txt"), "unicode");
        Files.writeString(root.resolve("holiday/sub dir/notes.txt"), "notes");
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

    /**
     * Mounts the image with Windows' own {@code Mount-DiskImage}, which needs no extra tools, and
     * returns the checksums of every file on it.
     */
    private Map<String, String> mountedChecksums(Path image) throws IOException {
        String quoted = "'" + image.toAbsolutePath().toString().replace("'", "''") + "'";
        // Windows assigns the drive letter shortly after mounting, so ask until it appears.
        String letter = powerShell("Mount-DiskImage -ImagePath " + quoted + " | Out-Null; "
                + "for ($i = 0; $i -lt 60; $i++) { "
                + "$letter = (Get-DiskImage -ImagePath " + quoted + " | Get-Volume).DriveLetter; "
                + "if ($letter) { $letter; break }; Start-Sleep -Milliseconds 500 }");
        try {
            assertTrue(letter.matches("[A-Za-z]"), "No drive letter for the mounted image: '" + letter + "'");
            return checksums(Path.of(letter + ":\\"), "");
        } finally {
            powerShell("Dismount-DiskImage -ImagePath " + quoted + " | Out-Null");
        }
    }

    private String powerShell(String command) {
        List<String> output = Collections.synchronizedList(new ArrayList<>());
        Path executable = WindowsPlatform.powerShell(System.getenv()).orElseThrow();
        ProcessResult result = runner.run(
                ProcessRequest.builder(executable, List.of("-NoProfile", "-NonInteractive", "-Command", command))
                        .onStdoutLine(output::add)
                        .build(),
                new CancelToken());
        assertTrue(result.succeeded(), result.technicalDetail());
        return String.join("", output).strip();
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
