// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.project;

import io.aerohelper.retroburn.audio.ProbedAudio;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The music files of an MP3 disc, in the order added, with the folders they came from. Immutable.
 * See {@code docs/design/LLD-mp3-disc.md} section 3.
 *
 * @param items the files
 */
public record Mp3Compilation(List<Item> items) {

    public Mp3Compilation {
        items = List.copyOf(items);
    }

    public static Mp3Compilation empty() {
        return new Mp3Compilation(List.of());
    }

    /**
     * One music file.
     *
     * @param source the file, never changed
     * @param audio what ffprobe found in it
     * @param folders the folders from the added folder (included) down to the file; empty for a file
     *     added on its own
     */
    public record Item(Path source, ProbedAudio audio, List<String> folders) {

        public Item {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(audio, "audio");
            folders = List.copyOf(folders);
        }

        /** True if the file is MP3 already and is copied unchanged. */
        public boolean isMp3() {
            return "mp3".equals(audio.codec());
        }

        /** The file name without its extension. */
        public String baseName() {
            String name = source.getFileName().toString();
            int dot = name.lastIndexOf('.');
            return dot > 0 ? name.substring(0, dot) : name;
        }

        /** The file name's extension in lower case, or "" when it has none. */
        public String extension() {
            String name = source.getFileName().toString();
            int dot = name.lastIndexOf('.');
            return dot > 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        }
    }

    public Mp3Compilation add(List<Item> added) {
        List<Item> all = new ArrayList<>(items);
        all.addAll(added);
        return new Mp3Compilation(all);
    }

    public Mp3Compilation remove(int index) {
        List<Item> all = new ArrayList<>(items);
        all.remove(index);
        return new Mp3Compilation(all);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }
}
