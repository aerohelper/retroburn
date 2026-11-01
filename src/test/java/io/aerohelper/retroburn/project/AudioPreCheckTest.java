// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aerohelper.retroburn.audio.CdAudio;
import io.aerohelper.retroburn.audio.ProbedAudio;
import io.aerohelper.retroburn.common.OperatingSystem;
import io.aerohelper.retroburn.common.UserMessage;
import io.aerohelper.retroburn.engine.ConnectionType;
import io.aerohelper.retroburn.engine.DriveId;
import io.aerohelper.retroburn.engine.DriveInfo;
import io.aerohelper.retroburn.engine.MediaInfo;
import io.aerohelper.retroburn.engine.MediaKind;
import io.aerohelper.retroburn.engine.MediaState;
import io.aerohelper.retroburn.platform.PowerStatus.PowerSource;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AudioPreCheckTest {

    private static final DriveInfo DRIVE =
            new DriveInfo(new DriveId("/dev/sr0"), "Vendor", "Writer", ConnectionType.OTHER, true);
    /** An 80-minute CD-R: 360,000 sectors of 2048 bytes free. */
    private static final MediaInfo BLANK_CD_80 =
            new MediaInfo(MediaKind.CD_R, MediaState.BLANK, CdAudio.SECTORS_80_MINUTES * 2048, 0);

    @Test
    void anAlbumThatFitsCanStart() {
        BurnPlan plan = check(compilation(12, Duration.ofMinutes(4)), BLANK_CD_80, true, true);
        assertTrue(plan.canStart(), plan.blockers().toString());
        assertEquals(JobKind.AUDIO_CD, plan.kind());
    }

    @Test
    void dvdsAreRefused() {
        MediaInfo dvd = new MediaInfo(MediaKind.DVD_PLUS_RW, MediaState.BLANK, 4_700_000_000L, 0);
        assertEquals(
                List.of("burn.blocker.audioNeedsCd"),
                keys(check(compilation(2, Duration.ofMinutes(3)), dvd, true, true)));
    }

    @Test
    void tooLongShowsTheExcessInMinutes() {
        // 21 tracks of 4 minutes = 84 minutes plus 42 seconds of gaps, on an 80-minute disc.
        BurnPlan plan = check(compilation(21, Duration.ofMinutes(4)), BLANK_CD_80, true, true);
        assertEquals(List.of(UserMessage.of("burn.blocker.audioTooLong", "4:42", "80:00")), plan.blockers());
    }

    @Test
    void trackCountAndLengthLimits() {
        assertEquals(List.of("burn.blocker.noTracks"), keys(check(AudioCompilation.empty(), BLANK_CD_80, true, true)));
        assertTrue(keys(check(compilation(100, Duration.ofSeconds(10)), BLANK_CD_80, true, true))
                .contains("burn.blocker.tooManyTracks"));
        assertEquals(
                List.of(UserMessage.of("burn.blocker.trackTooShort", 2)),
                check(
                                AudioCompilation.empty()
                                        .add(List.of(
                                                track("a", Duration.ofSeconds(30)), track("b", Duration.ofSeconds(3)))),
                                BLANK_CD_80,
                                true,
                                true)
                        .blockers());
    }

    @Test
    void cdTextProblemsBlockOnlyWhereItCanBeWritten() {
        AudioCompilation devanagari = AudioCompilation.empty()
                .add(List.of(new AudioCompilation.Track(Path.of("a.flac"), audio(Duration.ofMinutes(3)), "राग", "")));
        assertEquals(
                List.of(UserMessage.of("burn.blocker.cdText.needs_latin", 1, "title")),
                check(devanagari, BLANK_CD_80, true, true).blockers());
        assertTrue(check(devanagari, BLANK_CD_80, false, true).canStart(), "CD-Text off");
        BurnPlan windows = check(devanagari, BLANK_CD_80, true, false);
        assertTrue(windows.canStart());
        assertTrue(windows.warnings().contains(UserMessage.of("burn.warning.noCdText")));
    }

    @Test
    void cdTextOverTheBudgetIsBlocked() {
        String long79 = "x".repeat(79);
        List<AudioCompilation.Track> tracks = Collections.nCopies(
                40, new AudioCompilation.Track(Path.of("a.flac"), audio(Duration.ofSeconds(30)), long79, long79));
        AudioCompilation compilation = AudioCompilation.empty().add(tracks).withAlbum(long79, long79);
        assertEquals(List.of("burn.blocker.cdTextTooLong"), keys(check(compilation, BLANK_CD_80, true, true)));
    }

    @Test
    void workFolderMustHoldTheWavFiles() {
        AudioCompilation album = compilation(10, Duration.ofMinutes(5));
        BurnPreCheck.Request request = request(album, BLANK_CD_80, 100_000_000);
        assertEquals(
                List.of("burn.blocker.audioWorkFolderFull"), keys(AudioPreCheck.check(request, album, true, true)));
    }

    private static BurnPlan check(AudioCompilation compilation, MediaInfo media, boolean cdText, boolean engineCdText) {
        return AudioPreCheck.check(request(compilation, media, Long.MAX_VALUE), compilation, cdText, engineCdText);
    }

    private static BurnPreCheck.Request request(AudioCompilation compilation, MediaInfo media, long freeWorkBytes) {
        return new BurnPreCheck.Request(
                JobKind.AUDIO_CD,
                Optional.of(DRIVE),
                Optional.of(media),
                AudioPreCheck.blocksToWrite(compilation),
                Optional.of(new BurnPreCheck.WorkSpace(Path.of("work"), freeWorkBytes)),
                true,
                false,
                PowerSource.MAINS,
                Map.of(),
                OperatingSystem.LINUX);
    }

    private static List<String> keys(BurnPlan plan) {
        return plan.blockers().stream().map(UserMessage::key).toList();
    }

    private static AudioCompilation compilation(int tracks, Duration each) {
        return AudioCompilation.empty().add(Collections.nCopies(tracks, track("t", each)));
    }

    private static AudioCompilation.Track track(String title, Duration length) {
        return new AudioCompilation.Track(Path.of(title + ".flac"), audio(length), title, "");
    }

    private static ProbedAudio audio(Duration length) {
        return new ProbedAudio(length, "flac", "[0][0][0][0]", 44_100, 2, ProbedAudio.AudioTags.none());
    }
}
