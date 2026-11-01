// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.audio;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * Extended M3U playlists for MP3 discs: {@code #EXTM3U}, then {@code #EXTINF:<seconds>,<text>} and the
 * relative file name per track. ASCII {@code .m3u} for car stereos, UTF-8 {@code .m3u8} otherwise.
 * See {@code docs/design/LLD-mp3-disc.md} section 5.
 */
public final class M3uWriter {

    private M3uWriter() {}

    /**
     * One track in a playlist.
     *
     * @param fileName the file's name in the same folder
     * @param duration how long it plays
     * @param text "artist - title" as players show it
     */
    public record Entry(String fileName, Duration duration, String text) {}

    public static String extension(boolean car) {
        return car ? "m3u" : "m3u8";
    }

    public static Charset charset(boolean car) {
        return car ? StandardCharsets.US_ASCII : StandardCharsets.UTF_8;
    }

    /** The playlist's text, with CRLF line ends, which every player reads. */
    public static String render(List<Entry> entries, boolean car) {
        StringBuilder playlist = new StringBuilder("#EXTM3U\r\n");
        for (Entry entry : entries) {
            String text =
                    car ? CarNames.carText(entry.text(), "") : entry.text().replaceAll("[\\r\\n]", " ");
            playlist.append("#EXTINF:")
                    .append(Math.max(1, Math.round(entry.duration().toMillis() / 1000.0)))
                    .append(',')
                    .append(text)
                    .append("\r\n")
                    .append(entry.fileName())
                    .append("\r\n");
        }
        return playlist.toString();
    }
}
