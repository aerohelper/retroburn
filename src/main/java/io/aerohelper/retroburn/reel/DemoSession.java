// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.reel;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A made-up audio CD burn, for a demo reel without a drive. All names are invented. */
public final class DemoSession {

    private DemoSession() {}

    public static BurnSession create(String theme) {
        Instant started = Instant.parse("2026-01-01T12:00:00Z");
        long total = 4 * 60_000 + 30_000;
        List<BurnSession.Sample> samples = new ArrayList<>();
        for (long at = 0; at <= total; at += 1000) {
            double fraction = Math.min(1, at / (double) total);
            samples.add(new BurnSession.Sample(at, fraction, at < 20_000 ? "" : "16x"));
        }
        List<BurnSession.Marker> markers = List.of(
                new BurnSession.Marker(0, "step.convertTracks"),
                new BurnSession.Marker(60_000, "step.burnAudio"),
                new BurnSession.Marker(250_000, "step.checkTracks"));
        List<String> tracks = List.of(
                "Morning Static",
                "Neon Parkway",
                "Dial-Up Dreams",
                "Silver Disc Summer",
                "Pixel Rain",
                "Midnight Burn",
                "Last Track Standing");
        return new BurnSession(
                BurnSession.SCHEMA_VERSION,
                UUID.nameUUIDFromBytes("demo".getBytes()).toString(),
                started,
                started.plusMillis(total),
                "AUDIO_CD",
                "Demo Mix",
                theme,
                tracks,
                312_000_000L,
                "SUCCEEDED",
                samples,
                markers);
    }
}
