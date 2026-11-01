// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.windows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.common.OperationCancelledException;
import io.aerohelper.retroburn.common.RetroBurnException;
import io.aerohelper.retroburn.common.ToolFailedException;
import io.aerohelper.retroburn.engine.ConnectionType;
import io.aerohelper.retroburn.engine.DriveId;
import io.aerohelper.retroburn.engine.DriveInfo;
import io.aerohelper.retroburn.engine.MediaInfo;
import io.aerohelper.retroburn.platform.PowerStatus.PowerSource;
import io.aerohelper.retroburn.platform.SleepInhibitionUnavailableException;
import io.aerohelper.retroburn.tools.ProcessRunner;
import io.aerohelper.retroburn.tools.RunningProcess;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Runs {@link HelperClient} against {@link FakeHelper}, a real child process, on every OS. */
class HelperClientTest {

    @TempDir
    Path folder;

    private final ProcessRunner runner = new ProcessRunner();

    @Test
    void sendsTheRequestAsTheFirstLine() throws IOException {
        Path recorded = folder.resolve("request.json");
        HelperRequest request = HelperRequest.readMedia(new DriveId("drive \"one\""));

        client("record-request", recorded.toString())
                .run(request, event -> {}, CancelToken.none(), HelperClient.Limits.query());

        assertEquals(request.json(), Files.readString(recorded, StandardCharsets.UTF_8));
    }

    @Test
    void passesEventsOnAndEndsAtTheResult() {
        List<HelperEvent> events = Collections.synchronizedList(new ArrayList<>());

        client("replay", "windows-helper/build-image.txt")
                .run(HelperRequest.power(), events::add, CancelToken.none(), HelperClient.Limits.query());

        assertEquals(
                List.of(
                        new HelperEvent.Progress("image", 0, 9568256),
                        new HelperEvent.Progress("image", 9568256, 9568256)),
                events);
    }

    @Test
    void anErrorEventBecomesATypedException() {
        RetroBurnException error = assertThrows(
                RetroBurnException.class,
                () -> client("replay", "windows-helper/read-media-unknown-drive.txt")
                        .run(HelperRequest.power(), event -> {}, CancelToken.none(), HelperClient.Limits.query()));

        assertEquals("error.driveNotFound", error.userMessage().key());
        assertTrue(error.technicalDetail().contains("No drive with id no-such-drive"));
    }

    @Test
    void aHelperThatEndsWithoutAnswerIsUnavailable() {
        ToolFailedException error = assertThrows(
                ToolFailedException.class,
                () -> client("crash")
                        .run(HelperRequest.probe(), event -> {}, CancelToken.none(), HelperClient.Limits.query()));

        assertEquals("error.windowsHelperUnavailable", error.userMessage().key());
        assertTrue(error.technicalDetail().contains("Cannot add type"));
    }

    @Test
    void aSilentHelperTimesOut() {
        ToolFailedException error = assertThrows(
                ToolFailedException.class,
                () -> client("silent", "60000")
                        .run(
                                HelperRequest.probe(),
                                event -> {},
                                CancelToken.none(),
                                HelperClient.Limits.silentFor(Duration.ofSeconds(2))));

        assertEquals("error.windowsHelperTimedOut", error.userMessage().key());
    }

    @Test
    void cancellingClosesTheInputAndWaitsForTheHelperToStop() {
        CancelToken cancel = new CancelToken();
        List<HelperEvent> events = Collections.synchronizedList(new ArrayList<>());

        assertThrows(
                OperationCancelledException.class,
                () -> client("stop-when-asked")
                        .run(
                                HelperRequest.power(),
                                event -> {
                                    events.add(event);
                                    cancel.cancel();
                                },
                                cancel,
                                HelperClient.Limits.query()));

        assertEquals(List.of(new HelperEvent.Progress("writing", 1, 10)), events);
    }

    @Test
    void aLongRunningHelperStopsWhenClosed() throws InterruptedException {
        CountDownLatch reported = new CountDownLatch(1);

        RunningProcess process = client("replay-then-wait", "windows-helper/watch-drives.txt")
                .start(HelperRequest.watchDrives(), event -> {
                    if (event instanceof HelperEvent.Drives) {
                        reported.countDown();
                    }
                });

        assertTrue(reported.await(30, TimeUnit.SECONDS));
        assertTrue(process.isAlive());
        process.close();
        assertFalse(process.isAlive());
    }

    @Test
    void theEngineReadsDrivesAndMediaFromTheHelper() {
        Imapi2Engine drives = new Imapi2Engine(client("replay", "windows-helper/list-drives-usb.txt"));
        Imapi2Engine media = new Imapi2Engine(client("replay", "windows-helper/read-media-no-media.txt"));

        List<DriveInfo> found = drives.listDrives();

        assertEquals(1, found.size());
        assertEquals("Slimtype eBAU108  7 L", found.getFirst().displayName());
        assertEquals(ConnectionType.USB, found.getFirst().connection());
        assertEquals(MediaInfo.noMedia(), media.readMedia(found.getFirst().id()));
    }

    @Test
    void powerStatusIsUnknownWhenTheHelperFails() {
        assertEquals(PowerSource.MAINS, new HelperPowerStatus(client("replay", "windows-helper/power.txt")).current());
        assertEquals(PowerSource.UNKNOWN, new HelperPowerStatus(client("crash")).current());
    }

    @Test
    void theSleepInhibitorHoldsTheHelperUntilClosed() {
        ExecutionStateSleepInhibitor inhibitor =
                new ExecutionStateSleepInhibitor(client("replay-then-wait", "windows-helper/keep-awake.txt"));

        inhibitor.acquire("Burning a disc").close();
    }

    @Test
    void theSleepInhibitorReportsAHelperThatNeverGetsReady() {
        assertThrows(
                SleepInhibitionUnavailableException.class,
                () -> new ExecutionStateSleepInhibitor(client("crash")).acquire("Burning a disc"));
    }

    @Test
    void aMissingPowerShellIsReportedAsUnavailable() {
        HelperClient client = new HelperClient(runner, HelperClient.powerShell(Optional.empty(), () -> folder));

        ToolFailedException error = assertThrows(
                ToolFailedException.class,
                () -> client.run(HelperRequest.probe(), event -> {}, CancelToken.none(), HelperClient.Limits.query()));
        assertEquals("error.windowsHelperUnavailable", error.userMessage().key());
    }

    private HelperClient client(String... fakeArguments) {
        return new HelperClient(runner, () -> FakeHelper.launch(fakeArguments));
    }
}
