// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.project;

import io.aerohelper.retroburn.audio.CarNames;
import io.aerohelper.retroburn.audio.M3uWriter;
import io.aerohelper.retroburn.audio.Mp3Encoding;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Exactly what an MP3 disc will hold: each file's path on the disc, whether it is copied or
 * converted, and the playlists. Made before the job starts, so the screen shows what the disc will
 * be. See {@code docs/design/LLD-mp3-disc.md} sections 2, 3 and 5.
 *
 * @param entries the music files, in disc order
 * @param playlists the playlists, one per folder with tracks (none when switched off)
 * @param skipped files left out because they are not MP3 and converting is off
 * @param warnings folders with too many files, too many folders
 * @param estimatedBytes the image's estimated size
 */
public record Mp3DiscPlan(
        List<Entry> entries,
        List<Playlist> playlists,
        List<Path> skipped,
        List<Warning> warnings,
        long estimatedBytes) {

    /** Car stereos often stop listing files after this many in one folder. */
    public static final int MAX_FILES_PER_FOLDER = 255;
    /** Car stereos often stop listing folders after this many. */
    public static final int MAX_FOLDERS = 99;

    static final String UNKNOWN_ARTIST = "Unknown artist";
    static final String UNKNOWN_ALBUM = "Unknown album";
    static final int CAR_FOLDER_DEPTH = 2;

    public Mp3DiscPlan {
        entries = List.copyOf(entries);
        playlists = List.copyOf(playlists);
        skipped = List.copyOf(skipped);
        warnings = List.copyOf(warnings);
    }

    /**
     * How the disc is laid out.
     *
     * @param layout where files go
     * @param carCompat car stereo naming and depth rules
     * @param convert convert other formats to MP3 (otherwise they are left out)
     * @param bitrate the bitrate for converted files
     * @param playlists write one playlist per folder
     */
    public record Options(
            Mp3Layout layout, boolean carCompat, boolean convert, Mp3Encoding.Bitrate bitrate, boolean playlists) {

        public Options {
            Objects.requireNonNull(layout, "layout");
            Objects.requireNonNull(bitrate, "bitrate");
        }
    }

    /**
     * One music file on the disc.
     *
     * @param discPath where it goes, starting with '/'
     * @param item the file it comes from
     * @param convert it is converted to MP3 first; otherwise copied unchanged
     */
    public record Entry(String discPath, Mp3Compilation.Item item, boolean convert) {}

    /**
     * A playlist.
     *
     * @param discPath where it goes
     * @param text its content
     * @param charset how it is written
     */
    public record Playlist(String discPath, String text, Charset charset) {}

    /**
     * Something the user should know about the layout.
     *
     * @param kind which limit
     * @param folder the folder concerned, "" for the whole disc
     * @param count how many files or folders
     */
    public record Warning(Kind kind, String folder, int count) {

        public enum Kind {
            CROWDED_FOLDER,
            TOO_MANY_FOLDERS
        }
    }

    public boolean needsConversion() {
        return entries.stream().anyMatch(Entry::convert);
    }

    public Duration totalDuration() {
        return entries.stream().map(entry -> entry.item().audio().duration()).reduce(Duration.ZERO, Duration::plus);
    }

    public static Mp3DiscPlan of(Mp3Compilation compilation, Options options) {
        Map<String, Folder> folders = new LinkedHashMap<>();
        List<Path> skipped = new ArrayList<>();
        for (Mp3Compilation.Item item : compilation.items()) {
            if (!item.isMp3() && !options.convert()) {
                skipped.add(item.source());
                continue;
            }
            List<String> names = folderNames(item, options);
            String key = String.join("/", names).toLowerCase(Locale.ROOT);
            folders.computeIfAbsent(key, unused -> new Folder(names)).items.add(item);
        }

        List<Entry> entries = new ArrayList<>();
        List<Playlist> playlists = new ArrayList<>();
        List<Warning> warnings = new ArrayList<>();
        long fileBytes = 0;
        long blocks = 0;
        for (Folder folder : folders.values()) {
            folder.items.sort(Comparator.comparingInt(
                            (Mp3Compilation.Item item) -> item.audio().tags().discNumber())
                    .thenComparingInt(item -> item.audio().tags().trackNumber())
                    .thenComparing(item -> displayTitle(item).toLowerCase(Locale.ROOT)));
            String prefix = folder.names.isEmpty() ? "/" : "/" + String.join("/", folder.names) + "/";
            Set<String> used = new HashSet<>();
            List<M3uWriter.Entry> playlist = new ArrayList<>();
            int position = 0;
            for (Mp3Compilation.Item item : folder.items) {
                position++;
                boolean convert = !item.isMp3();
                String name = unique(fileName(item, position, options), used);
                entries.add(new Entry(prefix + name, item, convert));
                playlist.add(new M3uWriter.Entry(name, item.audio().duration(), playlistText(item)));
                long bytes = convert
                        ? Mp3Encoding.estimatedBytes(item.audio().duration(), options.bitrate())
                        : size(item.source());
                fileBytes += bytes;
                blocks += Math.ceilDiv(bytes, TreeSizeScanner.BLOCK_SIZE);
            }
            if (folder.items.size() > MAX_FILES_PER_FOLDER) {
                warnings.add(new Warning(Warning.Kind.CROWDED_FOLDER, prefix, folder.items.size()));
            }
            if (options.playlists()) {
                String base = folder.names.isEmpty() ? "Playlist" : folder.names.getLast();
                String extension = M3uWriter.extension(options.carCompat());
                String name = unique(
                        options.carCompat()
                                ? CarNames.carFile(base, extension)
                                : CarNames.plain(base, extension, "Playlist"),
                        used);
                String text = M3uWriter.render(playlist, options.carCompat());
                playlists.add(new Playlist(prefix + name, text, M3uWriter.charset(options.carCompat())));
                fileBytes += text.length();
                blocks += Math.ceilDiv(text.length(), TreeSizeScanner.BLOCK_SIZE);
            }
        }
        Set<String> allFolders = new HashSet<>();
        for (Folder folder : folders.values()) {
            for (int depth = 1; depth <= folder.names.size(); depth++) {
                allFolders.add(String.join("/", folder.names.subList(0, depth)).toLowerCase(Locale.ROOT));
            }
        }
        if (allFolders.size() > MAX_FOLDERS) {
            warnings.add(new Warning(Warning.Kind.TOO_MANY_FOLDERS, "", allFolders.size()));
        }
        long estimate = ImageSizeEstimate.bytes(
                new TreeSize(fileBytes, blocks, entries.size() + playlists.size(), allFolders.size(), List.of(), 0));
        return new Mp3DiscPlan(entries, playlists, skipped, warnings, estimate);
    }

    private static List<String> folderNames(Mp3Compilation.Item item, Options options) {
        List<String> names = switch (options.layout()) {
            case KEEP_FOLDERS -> item.folders();
            case ARTIST_ALBUM ->
                List.of(
                        item.audio()
                                .tags()
                                .albumArtist()
                                .or(() -> item.audio().tags().artist())
                                .orElse(UNKNOWN_ARTIST),
                        item.audio().tags().album().orElse(UNKNOWN_ALBUM));
            case FLAT -> List.of();
        };
        if (options.carCompat() && names.size() > CAR_FOLDER_DEPTH) {
            names = List.of(names.getFirst(), String.join(" - ", names.subList(1, names.size())));
        }
        return names.stream()
                .map(name -> options.carCompat() ? CarNames.carFolder(name) : CarNames.plain(name, "", "Folder"))
                .toList();
    }

    private static String fileName(Mp3Compilation.Item item, int position, Options options) {
        String extension = item.isMp3() ? item.extension() : "mp3";
        if (options.carCompat()) {
            String base = item.audio().tags().title().orElse(item.baseName());
            String prefixed = CarNames.prefixed(position, CarNames.carText(base, "Track"));
            return CarNames.carFile(prefixed, extension);
        }
        return CarNames.plain(item.baseName(), extension, "Track");
    }

    private static String displayTitle(Mp3Compilation.Item item) {
        return item.audio().tags().title().orElse(item.baseName());
    }

    private static String playlistText(Mp3Compilation.Item item) {
        String title = displayTitle(item);
        return item.audio()
                .tags()
                .artist()
                .map(artist -> artist + " - " + title)
                .orElse(title);
    }

    /** {@code name}, or {@code name (2)}, {@code (3)}… before the extension when already used here. */
    private static String unique(String name, Set<String> used) {
        String candidate = name;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        for (int number = 2; !used.add(candidate.toLowerCase(Locale.ROOT)); number++) {
            candidate = base + " (" + number + ")" + extension;
        }
        return candidate;
    }

    private static long size(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return 0;
        }
    }

    private static final class Folder {
        final List<String> names;
        final List<Mp3Compilation.Item> items = new ArrayList<>();

        Folder(List<String> names) {
            this.names = names;
        }
    }
}
