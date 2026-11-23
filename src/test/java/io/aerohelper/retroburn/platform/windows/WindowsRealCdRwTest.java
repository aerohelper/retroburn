// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.windows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.aerohelper.retroburn.audio.CdAudio;
import io.aerohelper.retroburn.audio.CdWavWriter;
import io.aerohelper.retroburn.audio.TrackListCheck;
import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.common.OperationCancelledException;
import io.aerohelper.retroburn.engine.AudioDisc;
import io.aerohelper.retroburn.engine.BlankMode;
import io.aerohelper.retroburn.engine.BurnEvent;
import io.aerohelper.retroburn.engine.BurnOptions;
import io.aerohelper.retroburn.engine.DiscEntry;
import io.aerohelper.retroburn.engine.DiscTree;
import io.aerohelper.retroburn.engine.DriveId;
import io.aerohelper.retroburn.engine.DriveInfo;
import io.aerohelper.retroburn.engine.ImageOptions;
import io.aerohelper.retroburn.engine.MediaInfo;
import io.aerohelper.retroburn.engine.MediaKind;
import io.aerohelper.retroburn.engine.MediaState;
import io.aerohelper.retroburn.engine.TrackLayout;
import io.aerohelper.retroburn.tools.ProcessRunner;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The CD-RW checks of the manual test list through the Java engines: data burn and verify, quick
 * erase, cancelling an audio CD, full erase, and an audio CD whose track list is read back and
 * compared. It destroys the disc's content, so it runs only when {@code
 * RETROBURN_ERASE_TEST_DISC=yes} is set, and only with a CD-RW in the first drive. Never set on CI.
 * The audio CD of the last step is left on the disc for a CD player.
 */
@EnabledOnOs(OS.WINDOWS)
@EnabledIfEnvironmentVariable(named = "RETROBURN_ERASE_TEST_DISC", matches = "yes")
class WindowsRealCdRwTest {

    @TempDir
    Path folder;

    private HelperClient helper() {
        return new HelperClient(
                new ProcessRunner(),
                HelperClient.powerShell(
                        WindowsPlatform.powerShell(System.getenv()),
                        new HelperFiles(folder.resolve("helper"))::script));
    }

    @Test
    void dataEraseAudioCancelFullEraseAndAudioCd() throws IOException {
        HelperClient helper = helper();
        Imapi2Engine engine = new Imapi2Engine(helper);
        Imapi2AudioEngine audio = new Imapi2AudioEngine(helper);
        DriveId drive = engine.listDrives().stream()
                .filter(DriveInfo::accessible)
                .findFirst()
                .orElseThrow()
                .id();
        MediaInfo media = engine.readMedia(drive);
        assumeTrue(media.kind() == MediaKind.CD_RW, "Insert a CD-RW: found " + media);
        if (media.state() != MediaState.BLANK) {
            engine.blank(drive, BlankMode.QUICK, event -> {}, new CancelToken());
        }

        // 1. Data: burn an image, compare it with the disc, quick erase.
        Path image = dataImage(helper);
        long start = System.nanoTime();
        engine.burnImage(drive, image, new BurnOptions(true), event -> {}, new CancelToken());
        engine.verifyImage(drive, image, event -> {}, new CancelToken());
        report("data burn and verify of " + Files.size(image) / (1024 * 1024) + " MB", start);
        start = System.nanoTime();
        engine.blank(drive, BlankMode.QUICK, event -> {}, new CancelToken());
        report("quick erase", start);
        assertEquals(MediaState.BLANK, engine.readMedia(drive).state());

        // 2. Audio: cancel part way through.
        AudioDisc disc = tones();
        CancelToken cancel = new CancelToken();
        Instant[] cancelledAt = new Instant[1];
        assertThrows(
                OperationCancelledException.class,
                () -> audio.burnAudioCd(
                        drive,
                        disc,
                        event -> {
                            if (event instanceof BurnEvent.Progress progress
                                    && progress.fraction() > 0.2
                                    && cancelledAt[0] == null) {
                                cancelledAt[0] = Instant.now();
                                cancel.cancel();
                            }
                        },
                        cancel));
        System.out.println("[cd-rw] cancelling the audio burn took "
                + Duration.between(cancelledAt[0], Instant.now()).toMillis() + " ms");
        System.out.println("[cd-rw] after cancel: " + engine.readMedia(drive));

        // 3. Full erase.
        start = System.nanoTime();
        List<BurnEvent> eraseEvents = new ArrayList<>();
        engine.blank(drive, BlankMode.FULL, eraseEvents::add, new CancelToken());
        report("full erase (" + eraseEvents.size() + " events)", start);
        assertEquals(MediaState.BLANK, engine.readMedia(drive).state());

        // 4. Audio CD, then the track list read back from the disc.
        start = System.nanoTime();
        audio.burnAudioCd(drive, disc, event -> {}, new CancelToken());
        report("audio CD of " + disc.tracks().size() + " tracks", start);
        TrackLayout layout = audio.readTrackLayout(drive);
        System.out.println("[cd-rw] track starts " + layout.trackStarts() + ", lead-out " + layout.leadOut()
                + ", spans " + layout.spans());
        List<Long> written =
                disc.tracks().stream().map(AudioDisc.Track::sectors).toList();
        Optional<String> difference = TrackListCheck.difference(written, layout);
        assertTrue(difference.isEmpty(), difference.orElse(""));
        System.out.println("[cd-rw] after the audio CD: " + engine.readMedia(drive));
    }

    private static void report(String what, long startNanos) {
        System.out.println("[cd-rw] " + what + " took " + (System.nanoTime() - startNanos) / 1_000_000_000 + " s");
    }

    /** Three tones (440, 660 and 880 Hz) of 20, 12 and 7.5 seconds, as CD audio WAV files. */
    private AudioDisc tones() throws IOException {
        List<AudioDisc.Track> tracks = new ArrayList<>();
        double[][] tones = {{440, 20}, {660, 12}, {880, 7.5}};
        for (int index = 0; index < tones.length; index++) {
            Path pcm = folder.resolve("tone" + index + ".pcm");
            int frames = (int) (tones[index][1] * CdAudio.SAMPLE_RATE);
            ByteBuffer samples = ByteBuffer.allocate(frames * 4).order(ByteOrder.LITTLE_ENDIAN);
            for (int frame = 0; frame < frames; frame++) {
                double fade = Math.min(1, Math.min(frame, frames - frame) / 2205.0);
                short value =
                        (short) (Math.sin(2 * Math.PI * tones[index][0] * frame / CdAudio.SAMPLE_RATE) * 8000 * fade);
                samples.putShort(value).putShort(value);
            }
            Files.write(pcm, samples.array());
            Path wav = folder.resolve("tone" + index + ".wav");
            long sectors = CdWavWriter.fromPcm(pcm, wav, new CancelToken());
            tracks.add(new AudioDisc.Track(wav, sectors));
        }
        return new AudioDisc(tracks, Optional.empty());
    }

    private Path dataImage(HelperClient helper) throws IOException {
        Path payload = folder.resolve("payload.bin");
        byte[] bytes = new byte[48 * 1024 * 1024];
        new Random(7).nextBytes(bytes);
        Files.write(payload, bytes);
        Path image = folder.resolve("disc.iso");
        new Imapi2ImageBuilder(helper)
                .buildDataImage(
                        DiscTree.of(List.of(new DiscEntry("/payload.bin", payload))),
                        new ImageOptions("RETROBURN_CDRW"),
                        image,
                        event -> {},
                        new CancelToken());
        return image;
    }
}
