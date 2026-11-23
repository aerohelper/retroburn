// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.tools;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.common.OperationCancelledException;
import io.aerohelper.retroburn.common.ToolFailedException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProcessRunnerTest {

    private final ProcessRunner runner = new ProcessRunner();

    @Test
    void deliversStdoutAndStderrLinesAndTheExitCode() {
        List<String> stdout = Collections.synchronizedList(new ArrayList<>());
        List<String> stderr = Collections.synchronizedList(new ArrayList<>());

        ProcessResult result = runner.run(
                request("both-streams")
                        .onStdoutLine(stdout::add)
                        .onStderrLine(stderr::add)
                        .build(),
                CancelToken.none());

        assertTrue(result.succeeded());
        assertEquals(List.of("out one", "out two"), stdout);
        assertEquals(List.of("err one"), stderr);
        assertTrue(result.outputTail().containsAll(List.of("out one", "err one", "out two")));
    }

    @Test
    void treatsCarriageReturnsAsLineEnds() {
        List<String> lines = Collections.synchronizedList(new ArrayList<>());

        runner.run(request("carriage-returns").onStdoutLine(lines::add).build(), CancelToken.none());

        assertEquals(List.of("10%", "20%", "30%", "done"), lines);
    }

    @Test
    void passesArgumentsWithoutShellInterpretation() {
        List<String> lines = Collections.synchronizedList(new ArrayList<>());

        runner.run(
                request("echo-arguments", "two words", "-not-an-option", "$HOME;rm -rf x", "")
                        .onStdoutLine(lines::add)
                        .build(),
                CancelToken.none());

        assertEquals(List.of("[two words]", "[-not-an-option]", "[$HOME;rm -rf x]", "[]"), lines);
    }

    @Test
    void reportsNonZeroExitCodesWithoutThrowing() {
        ProcessResult result = runner.run(request("exit", "7").build(), CancelToken.none());

        assertFalse(result.succeeded());
        assertEquals(7, result.exitCode());
        assertTrue(result.technicalDetail().contains("exit code 7"));
    }

    @Test
    void keepsOnlyTheMostRecentLines() {
        ProcessResult result = runner.run(request("many-lines", "500").build(), CancelToken.none());

        assertEquals(ProcessRunner.OUTPUT_TAIL_LINES, result.outputTail().size());
        assertEquals("line 500", result.outputTail().getLast());
    }

    @Test
    void stopsAProgramThatExceedsTheTotalTimeout() {
        ProcessResult result = runner.run(
                request("sleep", "60000").totalTimeout(Duration.ofMillis(500)).build(), CancelToken.none());

        assertTrue(result.timedOut());
        assertFalse(result.succeeded());
    }

    @Test
    void stopsAProgramThatGoesSilentButNotOneThatKeepsTalking() {
        ProcessResult silent = runner.run(
                request("sleep", "60000").idleTimeout(Duration.ofMillis(800)).build(), CancelToken.none());
        ProcessResult chatty = runner.run(
                request("chatty-sleep", "15")
                        .idleTimeout(Duration.ofMillis(800))
                        .build(),
                CancelToken.none());

        assertTrue(silent.timedOut());
        assertTrue(chatty.succeeded());
    }

    @Test
    void cancellingStopsTheProgramWithinTheCancelTarget() {
        CancelToken cancel = new CancelToken();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.schedule(cancel::cancel, 300, TimeUnit.MILLISECONDS);
        Instant start = Instant.now();
        try {
            assertThrows(
                    OperationCancelledException.class,
                    () -> runner.run(request("sleep", "60000").build(), cancel));
        } finally {
            scheduler.shutdownNow();
        }
        assertTrue(Duration.between(start, Instant.now()).compareTo(Duration.ofSeconds(5)) < 0);
    }

    @Test
    void reportsProgramsThatCannotStart() {
        ProcessRequest missing = ProcessRequest.builder(Path.of("definitely-not-a-program-xyz"), List.of())
                .build();

        assertThrows(ToolFailedException.class, () -> runner.run(missing, CancelToken.none()));
    }

    @Test
    void rethrowsAFailingLineConsumerAfterTheProgramEnds() {
        ProcessRequest request = request("both-streams")
                .onStdoutLine(line -> {
                    throw new IllegalStateException("consumer broke");
                })
                .build();

        IllegalStateException thrown =
                assertThrows(IllegalStateException.class, () -> runner.run(request, CancelToken.none()));
        assertEquals("consumer broke", thrown.getMessage());
    }

    @Test
    void longRunningProcessEndsWhenItsInputIsClosed() {
        RunningProcess process =
                runner.startLongRunning(request("wait-for-stdin-end").build());

        assertTrue(process.waitForExit(Duration.ofMillis(300)).isEmpty());
        assertTrue(process.isAlive());
        process.close();
        assertFalse(process.isAlive());
    }

    @Test
    void commandLineQuotesArgumentsWithSpacesForReadability() {
        ProcessRequest request = ProcessRequest.builder(Path.of("tool"), List.of("-a", "two words", ""))
                .build();

        assertEquals("tool -a 'two words' ''", request.commandLine());
    }

    @Test
    void copiesRawStdoutUnchangedIntoASink() {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        List<String> stderr = Collections.synchronizedList(new ArrayList<>());

        ProcessResult result = runner.run(
                request("binary", "100000")
                        .stdoutTo(sink)
                        .onStderrLine(stderr::add)
                        .build(),
                CancelToken.none());

        assertTrue(result.succeeded());
        byte[] data = sink.toByteArray();
        assertEquals(100_000, data.length);
        for (int i = 0; i < data.length; i++) {
            assertEquals((byte) (i % 251), data[i], "byte " + i);
        }
        assertEquals(List.of("binary done"), stderr);
    }

    @Test
    void feedsAFileAsStandardInput(@TempDir Path folder) throws IOException {
        byte[] data = new byte[70_000];
        new Random(3).nextBytes(data);
        Path input = Files.write(folder.resolve("in.bin"), data);
        ByteArrayOutputStream sink = new ByteArrayOutputStream();

        ProcessResult result =
                runner.run(request("copy-stdin").stdinFrom(input).stdoutTo(sink).build(), CancelToken.none());

        assertTrue(result.succeeded(), result.technicalDetail());
        assertArrayEquals(data, sink.toByteArray());
    }

    @Test
    void rethrowsAFailingSinkAfterTheProgramHasFinished() {
        OutputStream failing = new OutputStream() {
            @Override
            public void write(int value) throws IOException {
                throw new IOException("disk full");
            }
        };

        UncheckedIOException error = assertThrows(
                UncheckedIOException.class,
                () -> runner.run(request("binary", "1000000").stdoutTo(failing).build(), CancelToken.none()));

        assertEquals("disk full", error.getCause().getMessage());
    }

    private static ProcessRequest.Builder request(String... arguments) {
        List<String> command = ChildProgram.command(arguments);
        return ProcessRequest.builder(Path.of(command.getFirst()), command.subList(1, command.size()));
    }
}
