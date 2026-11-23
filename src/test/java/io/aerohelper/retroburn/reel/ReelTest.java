// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.reel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aerohelper.retroburn.common.Messages;
import io.aerohelper.retroburn.common.UserMessage;
import io.aerohelper.retroburn.engine.BurnEvent;
import io.aerohelper.retroburn.pipeline.PipelineEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import javax.sound.sampled.AudioSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReelTest {

    private final Messages messages = Messages.load(Locale.ENGLISH);

    @TempDir
    Path folder;

    /** A clock moved by hand. */
    static final class StepClock extends Clock {
        private Instant now = Instant.parse("2026-11-08T10:00:00Z");

        void advance(long millis) {
            now = now.plusMillis(millis);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    @Test
    void timelineSplitsTheReelIntoScenes() {
        ReelTimeline fifteen = ReelTimeline.of(15);
        assertEquals(450, fifteen.frameCount());
        assertEquals(
                List.of("intro", "spin-up", "progress", "complete", "end"),
                fifteen.spans().stream().map(span -> span.scene().id()).toList());
        assertEquals(7.0, fifteen.span("progress").length(), 1e-9);
        assertEquals(360, ReelTimeline.of(12).frameCount());
        assertEquals(540, ReelTimeline.of(18).frameCount());
        assertEquals(360, ReelTimeline.of(5).frameCount(), "clamped to 12 s");
        assertEquals(540, ReelTimeline.of(60).frameCount(), "clamped to 18 s");

        assertEquals("intro", fifteen.frame(0).scene().id());
        assertEquals("spin-up", fifteen.frame(60).scene().id());
        assertEquals(0, fifteen.frame(60).sceneFraction(), 1e-9);
        assertEquals("progress", fifteen.frame(105).scene().id());
        assertEquals("end", fifteen.frame(449).scene().id());
    }

    @Test
    void recorderKeepsOneSampleASecondTheSpeedAndTheSteps() {
        StepClock clock = new StepClock();
        SessionRecorder recorder = new SessionRecorder(clock, "AUDIO_CD", "Mix", "y2k", List.of("One", "Two"), 1000);
        recorder.onEvent(new PipelineEvent.StepStarted(0, 2, UserMessage.of("step.convertTracks")));
        for (int tick = 1; tick <= 25; tick++) {
            clock.advance(200);
            recorder.onEvent(new PipelineEvent.OverallProgress(tick / 50.0));
        }
        clock.advance(100);
        recorder.onEvent(new PipelineEvent.StepStarted(1, 2, UserMessage.of("step.burnAudio")));
        recorder.onEvent(
                new PipelineEvent.StepOutput(1, new BurnEvent.Progress(0.5, Optional.of("16x"), OptionalInt.empty())));
        clock.advance(1000);
        recorder.onEvent(new PipelineEvent.OverallProgress(0.9));

        BurnSession session = recorder.finish("SUCCEEDED");
        assertEquals("AUDIO_CD", session.kind());
        assertEquals("y2k", session.theme());
        assertEquals(6100, session.duration().toMillis());
        assertTrue(session.samples().size() <= 8, session.samples().toString());
        assertEquals(1.0, session.samples().getLast().fraction());
        assertEquals("16x", session.lastSpeed());
        assertEquals("step.convertTracks", session.stepAt(1000));
        assertEquals("step.burnAudio", session.stepAt(5200));
        // Samples at 0.2, 1.2 … 4.2 s, then 6.1 s (0.9) and the final 1.0.
        assertEquals(0.42, session.sampleAt(6099).fraction(), 1e-9);
        assertEquals(1.0, session.sampleAt(6100).fraction(), 1e-9);
        assertEquals(0, session.sampleAt(100).fraction());
        assertTrue(session.succeeded());
    }

    @Test
    void sessionsAreSavedReadAndPruned() throws IOException {
        SessionStore store = new SessionStore(folder);
        BurnSession demo = DemoSession.create("classic");
        Path file = store.save(demo);
        assertEquals(demo, store.read(file));
        for (int count = 0; count < SessionStore.KEEP + 5; count++) {
            BurnSession copy = new BurnSession(
                    1,
                    "id-" + count,
                    demo.started().plusSeconds(count),
                    demo.ended().plusSeconds(count),
                    demo.kind(),
                    demo.label(),
                    demo.theme(),
                    demo.items(),
                    demo.bytes(),
                    demo.outcome(),
                    List.of(),
                    List.of());
            store.save(copy);
        }
        List<Path> kept = store.list();
        assertEquals(SessionStore.KEEP, kept.size());
        assertTrue(
                kept.getFirst().getFileName().toString().contains("id-54"),
                kept.getFirst().toString());
    }

    @Test
    void scenesDrawEveryFrameKindAndArePure() {
        BurnSession session = DemoSession.create("y2k");
        ReelTimeline timeline = ReelTimeline.of(15);
        ReelRenderer first = new ReelRenderer(timeline, session, ReelStyle.of(session, messages, true));
        ReelRenderer second = new ReelRenderer(timeline, session, ReelStyle.of(session, messages, true));
        for (int frame : new int[] {0, 30, 70, 104, 200, 340, 400, 449}) {
            byte[] one = first.rgb(frame).clone();
            assertArrayEquals(one, second.rgb(frame), "frame " + frame);
            assertEquals(ReelStyle.WIDTH * ReelStyle.HEIGHT * 3, one.length);
        }
        BufferedImage spinStart = first.draw(61);
        int pixel = spinStart.getRGB(ReelStyle.WIDTH / 2, 200);
        assertNotEquals(0, pixel);
        assertNotEquals(
                java.util.Arrays.hashCode(first.rgb(200).clone()),
                java.util.Arrays.hashCode(first.rgb(201).clone()),
                "the disc turns between frames");
    }

    @Test
    void namesCanBeHidden() {
        BurnSession session = DemoSession.create("classic");
        assertEquals(
                List.of("Item 1", "Item 2"),
                ReelStyle.of(session, messages, true).itemNames().subList(0, 2));
        assertEquals(
                "Morning Static",
                ReelStyle.of(session, messages, false).itemNames().getFirst());
        assertEquals("Audio CD", ReelStyle.of(session, messages, true).kindName(session));
    }

    @Test
    void soundtrackLastsAsLongAsTheReelAndPlaysTheThemeSounds() throws Exception {
        ReelTimeline timeline = ReelTimeline.of(15);
        double[] mix = ReelSoundtrack.mix(timeline, "classic");
        assertEquals(15 * ReelSoundtrack.RATE, mix.length);
        assertTrue(peak(mix, 2.2, 3.0) > 0.05, "spin-up");
        assertTrue(peak(mix, 5, 8) > 0.02, "burning loop");
        assertTrue(peak(mix, 10.6, 11.2) > 0.05, "success");
        assertTrue(peak(mix, 0, 1.9) < 1e-9, "quiet intro");
        assertEquals(0, mix[mix.length - 1], 0.01, "faded out");

        Path wav = folder.resolve("mix.wav");
        ReelSoundtrack.write(mix, wav);
        try (var audio = AudioSystem.getAudioInputStream(wav.toFile())) {
            assertEquals(15.0, audio.getFrameLength() / audio.getFormat().getFrameRate(), 0.01);
        }
        assertEquals(0, ReelSoundtrack.read("/themes/none/sounds/spin-up.wav").length);
    }

    @Test
    void encoderPreferenceAndArguments() {
        assertEquals(
                "libx264",
                ReelEncoder.choose(Set.of("mpeg4", "libx264", "aac"))
                        .orElseThrow()
                        .name());
        ReelEncoder.Codec openh264 =
                ReelEncoder.choose(Set.of("mpeg4", "libopenh264")).orElseThrow();
        assertEquals("reel.encoder.openh264", openh264.noteKey());
        assertTrue(openh264.h264());
        ReelEncoder.Codec mpeg4 = ReelEncoder.choose(Set.of("mpeg4")).orElseThrow();
        assertFalse(mpeg4.h264());
        assertTrue(ReelEncoder.choose(Set.of("aac")).isEmpty());

        List<String> arguments = ReelEncoder.arguments(
                ReelEncoder.choose(Set.of("libx264")).orElseThrow(), Path.of("s.wav"), Path.of("out.mp4"));
        String line = String.join(" ", arguments);
        assertTrue(
                line.contains("-f rawvideo -pixel_format rgb24 -video_size 1080x1920 -framerate 30 -i pipe:0"), line);
        assertTrue(line.contains("-c:v libx264 -preset medium -crf 20 -pix_fmt yuv420p -c:a aac"), line);
        assertEquals("out.mp4", arguments.getLast());
        assertTrue(ReelMaker.fileName(DemoSession.create("classic")).matches("RetroBurn-\\d{8}-\\d{6}\\.mp4"));
    }

    private static double peak(double[] samples, double from, double to) {
        double peak = 0;
        for (int index = (int) (from * ReelSoundtrack.RATE); index < (int) (to * ReelSoundtrack.RATE); index++) {
            peak = Math.max(peak, Math.abs(samples[index]));
        }
        return peak;
    }
}
