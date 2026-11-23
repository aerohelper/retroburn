// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.project;

import io.aerohelper.retroburn.audio.CdAudio;
import io.aerohelper.retroburn.audio.CdTextBudget;
import io.aerohelper.retroburn.common.ByteSizes;
import io.aerohelper.retroburn.common.UserMessage;
import io.aerohelper.retroburn.engine.MediaInfo;
import io.aerohelper.retroburn.engine.MediaKind;
import io.aerohelper.retroburn.engine.MediaState;
import io.aerohelper.retroburn.tools.Feature;
import io.aerohelper.retroburn.tools.FeatureAvailability;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The audio CD rules on top of {@link BurnPreCheck}. The request's {@code bytesToWrite} counts the
 * disc's sectors as 2048-byte blocks, which is how a blank CD reports its free space too. See {@code
 * docs/design/LLD-audio-cd.md} section 7.
 */
public final class AudioPreCheck {

    private static final Set<MediaKind> AUDIO_MEDIA = Set.of(MediaKind.CD_R, MediaKind.CD_RW);

    /** Room in the work folder per byte of CD audio: the WAV files plus one track being converted. */
    static final double WORK_SPACE_FACTOR = 1.1;

    /** Generic results replaced by audio-specific ones. */
    private static final Set<String> REPLACED = Set.of("burn.blocker.tooBig", "burn.blocker.workFolderFull");

    private AudioPreCheck() {}

    /** The request for {@code compilation}: its length in 2048-byte blocks as {@code bytesToWrite}. */
    public static long blocksToWrite(AudioCompilation compilation) {
        return compilation.discSectors() * 2048;
    }

    /**
     * @param cdTextWanted the CD-Text setting
     * @param engineWritesCdText whether this system's engine can write CD-Text
     */
    public static BurnPlan check(
            BurnPreCheck.Request request,
            AudioCompilation compilation,
            boolean cdTextWanted,
            boolean engineWritesCdText) {
        BurnPlan base = BurnPreCheck.check(request);
        List<UserMessage> blockers = new ArrayList<>(base.blockers().stream()
                .filter(message -> !REPLACED.contains(message.key()))
                .toList());
        List<UserMessage> warnings = new ArrayList<>(base.warnings());

        if (compilation.tracks().isEmpty()) {
            blockers.add(UserMessage.of("burn.blocker.noTracks"));
        }
        if (compilation.tracks().size() > CdAudio.MAX_TRACKS) {
            blockers.add(UserMessage.of(
                    "burn.blocker.tooManyTracks", compilation.tracks().size()));
        }
        for (int index = 0; index < compilation.tracks().size(); index++) {
            if (compilation.tracks().get(index).sectors() < CdAudio.MIN_TRACK_SECTORS) {
                blockers.add(UserMessage.of("burn.blocker.trackTooShort", index + 1));
            }
        }
        request.media()
                .filter(media -> media.state() != MediaState.NO_MEDIA)
                .ifPresent(media -> checkMedia(media, compilation, blockers));
        if (cdTextWanted && engineWritesCdText) {
            compilation
                    .cdTextProblems()
                    .forEach(problem -> blockers.add(UserMessage.of(
                            "burn.blocker.cdText." + problem.status().name().toLowerCase(Locale.ROOT),
                            problem.track(),
                            problem.field().name().toLowerCase(Locale.ROOT))));
            if (compilation.cdTextProblems().isEmpty() && !CdTextBudget.fits(compilation.cdText())) {
                blockers.add(UserMessage.of("burn.blocker.cdTextTooLong"));
            }
        } else if (cdTextWanted) {
            warnings.add(UserMessage.of("burn.warning.noCdText"));
        }
        long workBytes = (long) (compilation.audioSectors() * CdAudio.BYTES_PER_SECTOR * WORK_SPACE_FACTOR);
        request.workFolder()
                .filter(space -> space.freeBytes() < workBytes)
                .ifPresent(space -> blockers.add(UserMessage.of(
                        "burn.blocker.audioWorkFolderFull",
                        space.folder().toString(),
                        ByteSizes.format(workBytes),
                        ByteSizes.format(space.freeBytes()))));
        FeatureAvailability audio = request.features().get(Feature.AUDIO_CD);
        if (audio != null && !audio.isAvailable()) {
            audio.reasons().stream()
                    .filter(reason -> !blockers.contains(reason))
                    .forEach(blockers::add);
        }
        return new BurnPlan(
                base.kind(),
                base.drive(),
                base.media(),
                base.bytesToWrite(),
                base.eraseFirst(),
                base.verify(),
                blockers,
                warnings,
                base.showUsbTip());
    }

    private static void checkMedia(MediaInfo media, AudioCompilation compilation, List<UserMessage> blockers) {
        if (!AUDIO_MEDIA.contains(media.kind())) {
            blockers.add(UserMessage.of("burn.blocker.audioNeedsCd"));
            return;
        }
        long capacity = DiscCapacity.usableBytes(media) / 2048;
        long needed = compilation.discSectors();
        if (needed > capacity) {
            blockers.add(UserMessage.of(
                    "burn.blocker.audioTooLong",
                    CdAudio.minutesAndSeconds(needed - capacity),
                    CdAudio.minutesAndSeconds(capacity)));
        }
    }
}
