// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.common.OperationCancelledException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Sector maths, the WAV writer and the FFmpeg arguments. */
class CdAudioTest {

    @TempDir
    Path folder;

    @Test
    void secondsBecomeSectorsRoundedUp() {
        assertEquals(75, CdAudio.sectorsFor(Duration.ofSeconds(1)));
        assertEquals(76, CdAudio.sectorsFor(Duration.ofMillis(1001)));
        assertEquals(1, CdAudio.sectorsFor(Duration.ofNanos(1)));
        assertEquals(1, CdAudio.sectorsForBytes(1));
        assertEquals(1, CdAudio.sectorsForBytes(2352));
        assertEquals(2, CdAudio.sectorsForBytes(2353));
    }

    @Test
    void discLengthCountsAGapPerTrack() {
        assertEquals(1000 + 3 * 150, CdAudio.discSectors(1000, 3));
    }

    @Test
    void timesAreFormatted() {
        assertEquals("74:00", CdAudio.minutesAndSeconds(CdAudio.SECTORS_74_MINUTES));
        assertEquals("0:01", CdAudio.minutesAndSeconds(149));
        assertEquals("0:2:0", CdAudio.minutesSecondsFrames(150));
        assertEquals("1:0:74", CdAudio.minutesSecondsFrames(60 * 75 + 74));
    }

    @Test
    void wavIsPaddedToWholeSectorsAndThePcmRemoved() throws IOException {
        Path pcm = folder.resolve("01.pcm");
        byte[] samples = new byte[2352 + 4];
        samples[0] = 7;
        samples[2352 + 3] = 9;
        Files.write(pcm, samples);
        Path wav = folder.resolve("01.wav");

        assertEquals(2, CdWavWriter.fromPcm(pcm, wav, new CancelToken()));

        byte[] written = Files.readAllBytes(wav);
        assertEquals(44 + 2 * 2352, written.length);
        assertEquals(7, written[44]);
        assertEquals(9, written[44 + 2352 + 3]);
        assertEquals(0, written[written.length - 1]);
        assertFalse(Files.exists(pcm));
    }

    @Test
    void emptyPcmIsAnError() throws IOException {
        Path pcm = Files.createFile(folder.resolve("empty.pcm"));
        assertThrows(IOException.class, () -> CdWavWriter.fromPcm(pcm, folder.resolve("x.wav"), new CancelToken()));
    }

    @Test
    void wavWritingStopsWhenCancelled() throws IOException {
        Path pcm = Files.write(folder.resolve("01.pcm"), new byte[100]);
        CancelToken cancel = new CancelToken();
        cancel.cancel();
        assertThrows(
                OperationCancelledException.class, () -> CdWavWriter.fromPcm(pcm, folder.resolve("01.wav"), cancel));
    }

    @Test
    void convertArgumentsAskForCdAudio() {
        List<String> arguments = FfmpegAudio.toCdPcmArguments(Path.of("in.flac"), Path.of("01.pcm"), false);
        assertEquals(
                List.of(
                        "-nostdin",
                        "-hide_banner",
                        "-loglevel",
                        "error",
                        "-y",
                        "-i",
                        "in.flac",
                        "-map",
                        "0:a:0",
                        "-vn",
                        "-sn",
                        "-dn",
                        "-ac",
                        "2",
                        "-ar",
                        "44100",
                        "-c:a",
                        "pcm_s16le",
                        "-f",
                        "s16le",
                        "-progress",
                        "pipe:1",
                        "-nostats",
                        "01.pcm"),
                arguments);
        assertTrue(FfmpegAudio.toCdPcmArguments(Path.of("in.flac"), Path.of("01.pcm"), true)
                .containsAll(List.of("-af", FfmpegAudio.LOUDNORM)));
    }

    @Test
    void progressComesFromOutTime() {
        Duration total = Duration.ofSeconds(10);
        assertEquals(OptionalDouble.of(0.25), FfmpegAudio.progress("out_time_us=2500000", total));
        assertEquals(OptionalDouble.of(1.0), FfmpegAudio.progress("out_time_us=99000000", total));
        assertEquals(OptionalDouble.of(1.0), FfmpegAudio.progress("progress=end", total));
        assertEquals(OptionalDouble.empty(), FfmpegAudio.progress("out_time_us=N/A", total));
        assertEquals(OptionalDouble.empty(), FfmpegAudio.progress("bitrate=1411.2kbits/s", total));
    }
}
