// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.project;

import io.aerohelper.retroburn.common.ByteSizes;
import io.aerohelper.retroburn.common.OperatingSystem;
import io.aerohelper.retroburn.common.UserMessage;
import io.aerohelper.retroburn.engine.ConnectionType;
import io.aerohelper.retroburn.engine.DriveInfo;
import io.aerohelper.retroburn.engine.MediaInfo;
import io.aerohelper.retroburn.engine.MediaKind;
import io.aerohelper.retroburn.engine.MediaState;
import io.aerohelper.retroburn.platform.PowerStatus.PowerSource;
import io.aerohelper.retroburn.tools.Feature;
import io.aerohelper.retroburn.tools.FeatureAvailability;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Decides whether a burn or erase can start and what to tell the user first. Pure logic: every
 * input is passed in. See {@code docs/design/LLD-data-burning.md} section 3, one rule per row.
 */
public final class BurnPreCheck {

    /** Rewritable discs that must be erased before they can be written again. */
    private static final Set<MediaKind> ERASE_BEFORE_WRITING = Set.of(MediaKind.CD_RW, MediaKind.DVD_RW);

    private BurnPreCheck() {}

    /**
     * Everything the checks look at.
     *
     * @param bytesToWrite image size, estimated for a data disc; 0 when there is nothing to write
     * @param workFolder where a data disc's image would go, with its free bytes; empty for other jobs
     * @param verify the user wants the disc checked afterwards
     * @param usbTipWanted the "show USB tip" setting
     * @param features feature availability from the tool check; a feature not in the map counts as
     *     available, because the check may still be running
     */
    public record Request(
            JobKind kind,
            Optional<DriveInfo> drive,
            Optional<MediaInfo> media,
            long bytesToWrite,
            Optional<WorkSpace> workFolder,
            boolean verify,
            boolean usbTipWanted,
            PowerSource power,
            Map<Feature, FeatureAvailability> features,
            OperatingSystem operatingSystem) {

        public Request {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(drive, "drive");
            Objects.requireNonNull(media, "media");
            Objects.requireNonNull(workFolder, "workFolder");
            Objects.requireNonNull(power, "power");
            features = Map.copyOf(features);
            Objects.requireNonNull(operatingSystem, "operatingSystem");
        }
    }

    /** A folder and the bytes free in it. */
    public record WorkSpace(Path folder, long freeBytes) {}

    public static BurnPlan check(Request request) {
        List<UserMessage> blockers = new ArrayList<>();
        List<UserMessage> warnings = new ArrayList<>();
        boolean eraseFirst = false;
        boolean verify = request.verify() && request.kind() != JobKind.ERASE;

        if (request.kind() == JobKind.DATA_DISC && request.bytesToWrite() == 0) {
            blockers.add(UserMessage.of("burn.blocker.nothingToBurn"));
        }
        Optional<DriveInfo> drive = request.drive();
        if (drive.isEmpty()) {
            blockers.add(UserMessage.of("burn.blocker.noDrive"));
        } else if (!drive.get().accessible()) {
            blockers.add(UserMessage.of(
                    request.operatingSystem() == OperatingSystem.LINUX
                            ? "burn.blocker.noPermissionLinux"
                            : "burn.blocker.noPermission"));
        } else {
            Optional<MediaInfo> media = request.media().filter(found -> found.state() != MediaState.NO_MEDIA);
            if (media.isEmpty()) {
                blockers.add(UserMessage.of("burn.blocker.noDisc"));
            } else if (request.kind() == JobKind.ERASE) {
                checkErase(media.get(), blockers);
            } else {
                eraseFirst = checkWrite(request, media.get(), blockers, warnings);
            }
        }
        request.workFolder()
                .filter(space -> space.freeBytes() < request.bytesToWrite())
                .ifPresent(space -> blockers.add(UserMessage.of(
                        "burn.blocker.workFolderFull",
                        space.folder().toString(),
                        ByteSizes.format(request.bytesToWrite()),
                        ByteSizes.format(space.freeBytes()))));
        requireFeature(request, Feature.BURNING, blockers);
        if (verify) {
            requireFeature(request, Feature.VERIFY, blockers);
        }

        boolean usb =
                drive.map(found -> found.connection() == ConnectionType.USB).orElse(false);
        boolean showUsbTip = usb && request.usbTipWanted();
        if (showUsbTip) {
            warnings.add(UserMessage.of("burn.warning.usbTip"));
        }
        if (request.power() == PowerSource.BATTERY) {
            warnings.add(UserMessage.of("burn.warning.battery"));
        }
        warnings.add(UserMessage.of("burn.note.sleep"));

        return new BurnPlan(
                request.kind(),
                drive,
                request.media(),
                request.bytesToWrite(),
                eraseFirst,
                verify,
                blockers,
                warnings,
                showUsbTip);
    }

    private static void checkErase(MediaInfo media, List<UserMessage> blockers) {
        if (!media.kind().isRewritable()) {
            blockers.add(UserMessage.of("burn.blocker.notRewritable"));
        } else if (media.state() == MediaState.BLANK) {
            blockers.add(UserMessage.of("burn.blocker.alreadyBlank"));
        }
    }

    /** Returns whether a quick erase must run first. */
    private static boolean checkWrite(
            Request request, MediaInfo media, List<UserMessage> blockers, List<UserMessage> warnings) {
        if (!media.kind().isWritable()) {
            blockers.add(UserMessage.of("burn.blocker.notWritable"));
            return false;
        }
        boolean hasData = media.state() != MediaState.BLANK;
        if (hasData && !media.kind().isRewritable()) {
            blockers.add(UserMessage.of("burn.blocker.writeOnceUsed"));
            return false;
        }
        long capacity = DiscCapacity.usableBytes(media);
        if (request.bytesToWrite() > capacity) {
            blockers.add(UserMessage.of(
                    "burn.blocker.tooBig",
                    ByteSizes.format(request.bytesToWrite() - capacity),
                    ByteSizes.format(capacity)));
        }
        if (hasData) {
            warnings.add(UserMessage.of("burn.warning.overwrite"));
        }
        return hasData && ERASE_BEFORE_WRITING.contains(media.kind());
    }

    private static void requireFeature(Request request, Feature feature, List<UserMessage> blockers) {
        FeatureAvailability availability = request.features().get(feature);
        if (availability != null && !availability.isAvailable()) {
            // Burning and verify usually fail for the same missing tool; say it once.
            availability.reasons().stream()
                    .filter(reason -> !blockers.contains(reason))
                    .forEach(blockers::add);
        }
    }
}
