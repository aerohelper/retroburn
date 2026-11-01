// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.windows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.common.OperationCancelledException;
import io.aerohelper.retroburn.engine.BlankMode;
import io.aerohelper.retroburn.engine.BurnEvent;
import io.aerohelper.retroburn.engine.BurnOptions;
import io.aerohelper.retroburn.engine.DiscEntry;
import io.aerohelper.retroburn.engine.DiscTree;
import io.aerohelper.retroburn.engine.DriveId;
import io.aerohelper.retroburn.engine.DriveInfo;
import io.aerohelper.retroburn.engine.ImageOptions;
import io.aerohelper.retroburn.tools.ProcessRunner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Burns, verifies, cancels and erases a real rewritable disc through the whole Java path. It
 * destroys the disc's content, so it runs only when {@code RETROBURN_ERASE_TEST_DISC=yes} is set,
 * with a DVD+RW (or other rewritable disc) in the first drive. Never set on CI.
 */
@EnabledOnOs(OS.WINDOWS)
@EnabledIfEnvironmentVariable(named = "RETROBURN_ERASE_TEST_DISC", matches = "yes")
class WindowsRealDiscTest {

    @TempDir
    Path folder;

    @Test
    void burnVerifyCancelAndErase() throws IOException {
        HelperFiles files = new HelperFiles(folder.resolve("helper"));
        HelperClient helper = new HelperClient(
                new ProcessRunner(),
                HelperClient.powerShell(WindowsPlatform.powerShell(System.getenv()), files::script));
        Imapi2Engine engine = new Imapi2Engine(helper);
        DriveId drive = engine.listDrives().stream()
                .filter(DriveInfo::accessible)
                .findFirst()
                .orElseThrow()
                .id();
        assertTrue(engine.readMedia(drive).kind().isRewritable(), "Insert a rewritable disc");
        Path image = image();
        List<BurnEvent> events = Collections.synchronizedList(new ArrayList<>());

        engine.burnImage(drive, image, new BurnOptions(true), events::add, new CancelToken());
        assertEquals(BurnEvent.Progress.of(1.0), events.getLast());
        engine.verifyImage(drive, image, events::add, new CancelToken());

        CancelToken cancel = new CancelToken();
        Instant[] cancelledAt = new Instant[1];
        assertThrows(
                OperationCancelledException.class,
                () -> engine.burnImage(
                        drive,
                        image,
                        new BurnOptions(true),
                        event -> {
                            if (event instanceof BurnEvent.Progress progress
                                    && progress.fraction() > 0.05
                                    && cancelledAt[0] == null) {
                                cancelledAt[0] = Instant.now();
                                cancel.cancel();
                            }
                        },
                        cancel));
        Duration stopping = Duration.between(cancelledAt[0], Instant.now());
        System.out.println("[real disc] cancelling the burn took " + stopping.toMillis() + " ms");
        assertTrue(stopping.compareTo(Duration.ofSeconds(30)) < 0, stopping.toString());

        engine.blank(drive, BlankMode.QUICK, events::add, new CancelToken());
        assertTrue(engine.readMedia(drive).kind().isRewritable());
    }

    private Path image() throws IOException {
        Path payload = folder.resolve("payload.bin");
        byte[] bytes = new byte[64 * 1024 * 1024];
        new Random(42).nextBytes(bytes);
        Files.write(payload, bytes);
        Path image = folder.resolve("disc.iso");
        new Imapi2ImageBuilder(new HelperClient(
                        new ProcessRunner(),
                        HelperClient.powerShell(
                                WindowsPlatform.powerShell(System.getenv()),
                                new HelperFiles(folder.resolve("helper"))::script)))
                .buildDataImage(
                        DiscTree.of(List.of(new DiscEntry("/payload.bin", payload))),
                        new ImageOptions("RETROBURN_TEST"),
                        image,
                        event -> {},
                        new CancelToken());
        return image;
    }
}
