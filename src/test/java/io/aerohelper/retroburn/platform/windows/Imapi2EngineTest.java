// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.windows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.common.OperationCancelledException;
import io.aerohelper.retroburn.common.RetroBurnException;
import io.aerohelper.retroburn.common.ToolFailedException;
import io.aerohelper.retroburn.engine.BlankMode;
import io.aerohelper.retroburn.engine.BurnEvent;
import io.aerohelper.retroburn.engine.BurnOptions;
import io.aerohelper.retroburn.engine.DriveId;
import io.aerohelper.retroburn.engine.MediaInfo;
import io.aerohelper.retroburn.engine.MediaKind;
import io.aerohelper.retroburn.engine.MediaState;
import io.aerohelper.retroburn.tools.ProcessRunner;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link Imapi2Engine} against helper output captured with a real DVD+RW. Runs on every OS. */
class Imapi2EngineTest {

    private static final DriveId DRIVE = new DriveId("\\\\?\\usbstor#cdrom");
    private static final Path IMAGE = Path.of("disc.iso");

    private final List<BurnEvent> events = Collections.synchronizedList(new ArrayList<>());

    @Test
    void aBurnReportsWrittenSectorsAndStaysBelowDoneUntilTheResult() {
        engine("windows-helper/burn-image-dvdplusrw.txt")
                .burnImage(DRIVE, IMAGE, new BurnOptions(true), events::add, new CancelToken());

        List<Double> fractions = fractions();
        assertEquals(0.0, fractions.getFirst());
        assertEquals(1.0, fractions.getLast());
        assertEquals(1, fractions.stream().filter(fraction -> fraction == 1.0).count());
        assertTrue(fractions.contains(16223.0 / 33344), fractions.toString());
        for (int i = 1; i < fractions.size(); i++) {
            assertTrue(fractions.get(i) >= fractions.get(i - 1), "progress went backwards: " + fractions);
        }
        List<String> stages = events.stream()
                .filter(BurnEvent.LogLine.class::isInstance)
                .map(event -> ((BurnEvent.LogLine) event).text())
                .toList();
        assertEquals(
                List.of(
                        "Stage: initialising",
                        "Stage: calibrating",
                        "Stage: writing",
                        "Stage: finalising",
                        "Stage: verifying",
                        "Stage: completed"),
                stages);
    }

    @Test
    void aCancelledBurnEndsAsCancelled() {
        assertThrows(
                OperationCancelledException.class,
                () -> engine("windows-helper/burn-image-cancelled.txt")
                        .burnImage(DRIVE, IMAGE, new BurnOptions(true), events::add, new CancelToken()));
    }

    @Test
    void imapiRefusalsKeepTheirMessageAndCode() {
        ToolFailedException error = assertThrows(
                ToolFailedException.class,
                () -> engine("windows-helper/burn-image-without-force-overwrite.txt")
                        .burnImage(DRIVE, IMAGE, new BurnOptions(true), events::add, new CancelToken()));

        assertEquals("error.imapiFailed", error.userMessage().key());
        assertTrue(error.technicalDetail().contains("0xC0AA0405"), error.technicalDetail());
    }

    @Test
    void anEraseShowsNoMadeUpPercentage() {
        engine("windows-helper/blank-quick-dvdplusrw.txt")
                .blank(DRIVE, BlankMode.QUICK, events::add, new CancelToken());

        assertEquals(List.of(1.0), fractions());
    }

    @Test
    void anEraseCancelledBeforeItStartsNeverReachesTheDrive() {
        CancelToken cancel = new CancelToken();
        cancel.cancel();
        Imapi2Engine engine = new Imapi2Engine(new HelperClient(new ProcessRunner(), () -> {
            throw new AssertionError("The helper must not start");
        }));

        assertThrows(OperationCancelledException.class, () -> engine.blank(DRIVE, BlankMode.FULL, events::add, cancel));
    }

    @Test
    void verifyReportsBytesComparedAndMismatches() {
        engine("windows-helper/verify-image-match.txt").verifyImage(DRIVE, IMAGE, events::add, new CancelToken());
        assertEquals(0.0, fractions().getFirst());
        assertEquals(1.0, fractions().getLast());

        RetroBurnException mismatch = assertThrows(
                RetroBurnException.class,
                () -> engine("windows-helper/verify-image-mismatch.txt")
                        .verifyImage(DRIVE, IMAGE, events::add, new CancelToken()));
        assertEquals("error.verifyMismatch", mismatch.userMessage().key());
        assertTrue(mismatch.technicalDetail().contains("byte 32808"), mismatch.technicalDetail());
    }

    @Test
    void aWrittenDvdPlusRwIsWritableAgain() {
        MediaInfo media = engine("windows-helper/read-media-dvdplusrw.txt").readMedia(DRIVE);

        assertEquals(MediaKind.DVD_PLUS_RW, media.kind());
        assertEquals(MediaState.APPENDABLE, media.state());
        assertEquals(2_295_104L * 2048, media.freeBytes());
    }

    private List<Double> fractions() {
        synchronized (events) {
            return events.stream()
                    .filter(BurnEvent.Progress.class::isInstance)
                    .map(event -> ((BurnEvent.Progress) event).fraction())
                    .toList();
        }
    }

    private static Imapi2Engine engine(String capture) {
        return new Imapi2Engine(new HelperClient(new ProcessRunner(), () -> FakeHelper.launch("replay", capture)));
    }
}
