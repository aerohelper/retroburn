// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.project;

import io.aerohelper.retroburn.audio.CdAudio;
import io.aerohelper.retroburn.audio.CdTextRules;
import io.aerohelper.retroburn.audio.ProbedAudio;
import io.aerohelper.retroburn.engine.CdText;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * The tracks of an audio CD and its texts. Immutable; every change returns a new compilation. See
 * {@code docs/design/LLD-audio-cd.md} section 3.
 *
 * @param albumTitle the album's title as typed
 * @param albumPerformer the album's performer as typed
 * @param tracks the tracks, in disc order
 */
public record AudioCompilation(String albumTitle, String albumPerformer, List<Track> tracks) {

    public AudioCompilation {
        Objects.requireNonNull(albumTitle, "albumTitle");
        Objects.requireNonNull(albumPerformer, "albumPerformer");
        tracks = List.copyOf(tracks);
    }

    public static AudioCompilation empty() {
        return new AudioCompilation("", "", List.of());
    }

    /**
     * One track.
     *
     * @param source the music file, never changed
     * @param audio what ffprobe found in it
     * @param title the title as typed
     * @param performer the performer as typed
     */
    public record Track(Path source, ProbedAudio audio, String title, String performer) {

        public Track {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(audio, "audio");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(performer, "performer");
        }

        /** A track with its title and performer taken from the tags, or the file name. */
        public static Track of(Path source, ProbedAudio audio) {
            String fileName = source.getFileName().toString();
            int dot = fileName.lastIndexOf('.');
            String baseName = dot > 0 ? fileName.substring(0, dot) : fileName;
            return new Track(
                    source,
                    audio,
                    audio.tags().title().orElse(baseName),
                    audio.tags().artist().orElse(""));
        }

        public long sectors() {
            return CdAudio.sectorsFor(audio.duration());
        }
    }

    /**
     * Adds tracks at the end. When the album texts are still empty they are filled from the tags:
     * the album title from an {@code album} tag all tracks share, the performer from {@code
     * album_artist}, or from {@code artist} when all tracks share it.
     */
    public AudioCompilation add(List<Track> added) {
        List<Track> all = new ArrayList<>(tracks);
        all.addAll(added);
        String title = albumTitle.isEmpty()
                ? shared(all, t -> t.audio().tags().album()).orElse("")
                : albumTitle;
        String performer = albumPerformer.isEmpty()
                ? shared(all, t -> t.audio().tags().albumArtist())
                        .or(() -> shared(all, t -> t.audio().tags().artist()))
                        .orElse("")
                : albumPerformer;
        return new AudioCompilation(title, performer, all);
    }

    public AudioCompilation remove(int index) {
        List<Track> all = new ArrayList<>(tracks);
        all.remove(index);
        return new AudioCompilation(albumTitle, albumPerformer, all);
    }

    /** Moves the track at {@code from} so that it ends up at {@code to}. */
    public AudioCompilation move(int from, int to) {
        List<Track> all = new ArrayList<>(tracks);
        all.add(to, all.remove(from));
        return new AudioCompilation(albumTitle, albumPerformer, all);
    }

    public AudioCompilation withTrack(int index, Track track) {
        List<Track> all = new ArrayList<>(tracks);
        all.set(index, track);
        return new AudioCompilation(albumTitle, albumPerformer, all);
    }

    public AudioCompilation withAlbum(String title, String performer) {
        return new AudioCompilation(title, performer, tracks);
    }

    /** Sectors of audio, without gaps. */
    public long audioSectors() {
        return tracks.stream().mapToLong(Track::sectors).sum();
    }

    /** Sectors on the disc, with a 2-second gap before every track. */
    public long discSectors() {
        return CdAudio.discSectors(audioSectors(), tracks.size());
    }

    /** The CD-Text to write, with every field cleaned; check {@link #cdTextProblems()} first. */
    public CdText cdText() {
        return new CdText(
                clean(albumTitle),
                clean(albumPerformer),
                tracks.stream()
                        .map(track -> new CdText.Track(clean(track.title()), clean(track.performer())))
                        .toList());
    }

    /**
     * Fields that cannot be written as CD-Text.
     *
     * @return one entry per problem; track is 0 for the album
     */
    public List<CdTextProblem> cdTextProblems() {
        List<CdTextProblem> problems = new ArrayList<>();
        check(problems, 0, CdTextProblem.Field.TITLE, albumTitle);
        check(problems, 0, CdTextProblem.Field.PERFORMER, albumPerformer);
        for (int index = 0; index < tracks.size(); index++) {
            check(
                    problems,
                    index + 1,
                    CdTextProblem.Field.TITLE,
                    tracks.get(index).title());
            check(
                    problems,
                    index + 1,
                    CdTextProblem.Field.PERFORMER,
                    tracks.get(index).performer());
        }
        return List.copyOf(problems);
    }

    /**
     * A field that blocks CD-Text.
     *
     * @param track 1-based track number, 0 for the album
     * @param field which field
     * @param status why it cannot be written
     */
    public record CdTextProblem(int track, Field field, CdTextRules.Status status) {

        public enum Field {
            TITLE,
            PERFORMER
        }
    }

    private static void check(List<CdTextProblem> problems, int track, CdTextProblem.Field field, String text) {
        CdTextRules.Status status = CdTextRules.clean(text).status();
        if (status.blocksCdText()) {
            problems.add(new CdTextProblem(track, field, status));
        }
    }

    private static String clean(String text) {
        return CdTextRules.clean(text).text();
    }

    private static Optional<String> shared(List<Track> all, Function<Track, Optional<String>> value) {
        List<Optional<String>> values = all.stream().map(value).distinct().toList();
        return values.size() == 1 ? values.getFirst() : Optional.empty();
    }
}
