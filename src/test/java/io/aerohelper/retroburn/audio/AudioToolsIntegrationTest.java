// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.engine.AudioDisc;
import io.aerohelper.retroburn.engine.CdText;
import io.aerohelper.retroburn.tools.ExternalTool;
import io.aerohelper.retroburn.tools.PathSearchToolLocator;
import io.aerohelper.retroburn.tools.ProcessRequest;
import io.aerohelper.retroburn.tools.ProcessResult;
import io.aerohelper.retroburn.tools.ProcessRunner;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs the real FFmpeg, ffprobe and cdrdao on synthetic tones: probe, convert to CD audio, check
 * the WAV, and have cdrdao parse and size the TOC. Runs wherever the tools are installed (the Linux
 * CI runner installs them). Tool output is printed so it can be captured as fixtures.
 */
class AudioToolsIntegrationTest {

    @TempDir
    Path folder;

    private final ProcessRunner runner = new ProcessRunner();
    private final PathSearchToolLocator locator = new PathSearchToolLocator(
            PathSearchToolLocator.foldersFromPathVariable(System.getenv("PATH"), File.pathSeparator),
            List.of("", ".exe"));

    @Test
    void taggedToneIsProbedConvertedAndProbedAgain() throws IOException {
        Path ffmpeg = require(ExternalTool.FFMPEG);
        Path ffprobe = require(ExternalTool.FFPROBE);
        Path source = folder.resolve("tone.flac");
        run(
                ffmpeg,
                List.of(
                        "-nostdin",
                        "-hide_banner",
                        "-loglevel",
                        "error",
                        "-y",
                        "-f",
                        "lavfi",
                        "-i",
                        "sine=frequency=440:sample_rate=48000:duration=5.5",
                        "-ac",
                        "1",
                        "-metadata",
                        "title=Song of Joy",
                        "-metadata",
                        "ARTIST=Felix",
                        "-metadata",
                        "album=Joyful Nights",
                        "-metadata",
                        "album_artist=United Cat Orchestra",
                        source.toString()));

        String json = String.join("\n", run(ffprobe, FfmpegAudio.probeArguments(source)));
        System.out.println("ffprobe JSON for a tagged FLAC:\n" + json);
        ProbedAudio probed = FfprobeJsonParser.parse(json).orElseThrow();
        assertEquals("flac", probed.codec());
        assertEquals(48_000, probed.sampleRate());
        assertEquals(1, probed.channels());
        assertEquals(5500, probed.duration().toMillis(), 20);
        assertEquals(Optional.of("Song of Joy"), probed.tags().title());
        assertEquals(Optional.of("Felix"), probed.tags().artist());
        assertEquals(Optional.of("Joyful Nights"), probed.tags().album());
        assertEquals(Optional.of("United Cat Orchestra"), probed.tags().albumArtist());
        assertFalse(probed.copyProtected());

        for (boolean normalize : List.of(false, true)) {
            Path pcm = folder.resolve("01.pcm");
            Path wav = folder.resolve(normalize ? "01-loud.wav" : "01.wav");
            List<String> progress = run(ffmpeg, FfmpegAudio.toCdPcmArguments(source, pcm, normalize));
            System.out.println(
                    "ffmpeg -progress output (normalize=" + normalize + "):\n" + String.join("\n", progress));
            List<Double> fractions = progress.stream()
                    .map(line -> FfmpegAudio.progress(line, probed.duration()))
                    .filter(OptionalDouble::isPresent)
                    .map(OptionalDouble::getAsDouble)
                    .toList();
            assertFalse(fractions.isEmpty(), "ffmpeg printed no progress");
            assertEquals(1.0, fractions.getLast());

            long sectors = CdWavWriter.fromPcm(pcm, wav, new CancelToken());
            assertEquals(CdAudio.sectorsFor(probed.duration()), sectors, 1.0);
            assertEquals(AudioDisc.WAV_HEADER_BYTES + sectors * CdAudio.BYTES_PER_SECTOR, Files.size(wav));

            String wavJson = String.join("\n", run(ffprobe, FfmpegAudio.probeArguments(wav)));
            ProbedAudio converted = FfprobeJsonParser.parse(wavJson).orElseThrow();
            assertEquals("pcm_s16le", converted.codec());
            assertEquals(CdAudio.SAMPLE_RATE, converted.sampleRate());
            assertEquals(CdAudio.CHANNELS, converted.channels());
            assertEquals(sectors * 1000 / 75, converted.duration().toMillis(), 20);
        }
    }

    @Test
    void fileWithoutSoundHasNoAudio() {
        Path ffmpeg = require(ExternalTool.FFMPEG);
        Path ffprobe = require(ExternalTool.FFPROBE);
        Path picture = folder.resolve("still.png");
        run(
                ffmpeg,
                List.of(
                        "-nostdin",
                        "-hide_banner",
                        "-loglevel",
                        "error",
                        "-y",
                        "-f",
                        "lavfi",
                        "-i",
                        "color=c=blue:s=16x16:d=1",
                        "-frames:v",
                        "1",
                        picture.toString()));
        String json = String.join("\n", run(ffprobe, FfmpegAudio.probeArguments(picture)));
        assertTrue(FfprobeJsonParser.parse(json).isEmpty());
    }

    @Test
    void cdrdaoParsesAndSizesTheToc() throws IOException {
        Path ffmpeg = require(ExternalTool.FFMPEG);
        Path cdrdao = locate("cdrdao");
        List<AudioDisc.Track> tracks = new ArrayList<>();
        for (String name : List.of("01", "02")) {
            Path pcm = folder.resolve(name + ".pcm");
            run(
                    ffmpeg,
                    List.of(
                            "-nostdin",
                            "-hide_banner",
                            "-loglevel",
                            "error",
                            "-y",
                            "-f",
                            "lavfi",
                            "-i",
                            "sine=frequency=440:duration=4.5",
                            "-ac",
                            "2",
                            "-ar",
                            "44100",
                            "-c:a",
                            "pcm_s16le",
                            "-f",
                            "s16le",
                            pcm.toString()));
            long sectors = CdWavWriter.fromPcm(pcm, folder.resolve(name + ".wav"), new CancelToken());
            tracks.add(new AudioDisc.Track(Path.of(name + ".wav"), sectors));
        }
        String accented = CdTextRules.clean("Café \"Mee\" Dvořák").text();
        AudioDisc disc = new AudioDisc(
                tracks,
                Optional.of(new CdText(
                        "Joyful Nights",
                        "United Cat Orchestra",
                        List.of(new CdText.Track("Song of Joy", "Felix"), new CdText.Track(accented, "Mia")))));
        Path toc = folder.resolve("disc.toc");
        CdrdaoTocWriter.write(disc, toc);

        List<String> shown = run(cdrdao, List.of("show-toc", toc.getFileName().toString()));
        System.out.println("cdrdao show-toc:\n" + String.join("\n", shown));
        assertTrue(shown.stream().anyMatch(line -> line.contains("Song of Joy")), "CD-Text missing");

        List<String> size = run(cdrdao, List.of("toc-size", toc.getFileName().toString()));
        System.out.println("cdrdao toc-size:\n" + String.join("\n", size));
        long expected = CdAudio.discSectors(disc.audioSectors(), tracks.size());
        assertTrue(
                size.stream().anyMatch(line -> line.matches(".*\\b" + expected + "\\b.*")),
                "expected " + expected + " blocks");
    }

    private Path require(ExternalTool tool) {
        Optional<Path> path = locator.locate(tool);
        assumeTrue(path.isPresent(), tool + " is not installed");
        return path.get();
    }

    private static Path locate(String name) {
        for (Path folder : PathSearchToolLocator.foldersFromPathVariable(System.getenv("PATH"), File.pathSeparator)) {
            for (String extension : List.of("", ".exe")) {
                Path candidate = folder.resolve(name + extension);
                if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                    return candidate;
                }
            }
        }
        assumeTrue(false, name + " is not installed");
        throw new AssertionError();
    }

    /** Runs a tool in the test folder and returns every stdout and stderr line. */
    private List<String> run(Path executable, List<String> arguments) {
        List<String> lines = Collections.synchronizedList(new ArrayList<>());
        ProcessResult result = runner.run(
                ProcessRequest.builder(executable, arguments)
                        .workingDirectory(folder)
                        .totalTimeout(Duration.ofMinutes(2))
                        .onStdoutLine(lines::add)
                        .onStderrLine(lines::add)
                        .build(),
                new CancelToken());
        assertTrue(result.succeeded(), () -> result.technicalDetail() + "\n" + String.join("\n", lines));
        return List.copyOf(lines);
    }
}
