// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.reel;

import java.util.ArrayList;
import java.util.List;

/**
 * Which scene plays when: intro 2 s, spin-up 1.5 s, progress (the rest), complete 2.5 s, end card
 * 2 s. 30 frames per second. See {@code docs/design/LLD-burn-reel.md} section 3.
 */
public final class ReelTimeline {

    public static final int FPS = 30;
    public static final int MIN_SECONDS = 12;
    public static final int MAX_SECONDS = 18;
    public static final int DEFAULT_SECONDS = 15;

    /** A scene and when it plays. */
    public record Span(ReelScene scene, double start, double end) {

        public double length() {
            return end - start;
        }
    }

    private final List<Span> spans;
    private final double seconds;

    private ReelTimeline(List<Span> spans, double seconds) {
        this.spans = List.copyOf(spans);
        this.seconds = seconds;
    }

    /** The standard scenes for a reel of {@code seconds} (clamped to 12–18). */
    public static ReelTimeline of(int seconds) {
        int total = Math.clamp(seconds, MIN_SECONDS, MAX_SECONDS);
        double progress = total - 2.0 - 1.5 - 2.5 - 2.0;
        record Part(ReelScene scene, double length) {}
        List<Span> spans = new ArrayList<>();
        double at = 0;
        for (Part part : List.of(
                new Part(new ReelScenes.Intro(), 2.0),
                new Part(new ReelScenes.SpinUp(), 1.5),
                new Part(new ReelScenes.Progress(), progress),
                new Part(new ReelScenes.Complete(), 2.5),
                new Part(new ReelScenes.EndCard(), 2.0))) {
            spans.add(new Span(part.scene(), at, at + part.length()));
            at += part.length();
        }
        return new ReelTimeline(spans, total);
    }

    public double seconds() {
        return seconds;
    }

    public int frameCount() {
        return (int) Math.round(seconds * FPS);
    }

    public List<Span> spans() {
        return spans;
    }

    public Span span(String sceneId) {
        return spans.stream()
                .filter(span -> span.scene().id().equals(sceneId))
                .findFirst()
                .orElseThrow();
    }

    /** Frame {@code index}: its time, its scene and how far into the scene it is. */
    public ReelFrame frame(int index) {
        double time = index / (double) FPS;
        for (Span span : spans) {
            if (time < span.end() || span == spans.getLast()) {
                double into = Math.clamp((time - span.start()) / span.length(), 0, 1);
                return new ReelFrame(index, time, span.scene(), into);
            }
        }
        throw new IllegalStateException("No scene at " + time);
    }
}
