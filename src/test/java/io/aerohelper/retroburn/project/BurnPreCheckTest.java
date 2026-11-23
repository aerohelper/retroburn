// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aerohelper.retroburn.common.Messages;
import io.aerohelper.retroburn.common.OperatingSystem;
import io.aerohelper.retroburn.common.UserMessage;
import io.aerohelper.retroburn.engine.ConnectionType;
import io.aerohelper.retroburn.engine.DriveId;
import io.aerohelper.retroburn.engine.DriveInfo;
import io.aerohelper.retroburn.engine.MediaInfo;
import io.aerohelper.retroburn.engine.MediaKind;
import io.aerohelper.retroburn.engine.MediaState;
import io.aerohelper.retroburn.platform.PowerStatus.PowerSource;
import io.aerohelper.retroburn.tools.Feature;
import io.aerohelper.retroburn.tools.FeatureAvailability;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** One test per row of the pre-check table in {@code docs/design/LLD-data-burning.md} section 3. */
class BurnPreCheckTest {

    private static final long DVD = 4_700_372_992L;
    private static final DriveInfo INTERNAL =
            new DriveInfo(new DriveId("/dev/sr0"), "Vendor", "Writer", ConnectionType.OTHER, true);
    private static final MediaInfo BLANK_DVD = new MediaInfo(MediaKind.DVD_R, MediaState.BLANK, DVD, 0);

    @Test
    void aBlankDiscThatFitsCanBeBurned() {
        BurnPlan plan = BurnPreCheck.check(request(JobKind.DATA_DISC, BLANK_DVD, 1_000_000));

        assertTrue(plan.canStart(), plan.blockers().toString());
        assertEquals(List.of(UserMessage.of("burn.note.sleep")), plan.warnings());
        assertFalse(plan.eraseFirst());
        assertTrue(plan.verify());
        assertFalse(plan.showUsbTip());
    }

    @Test
    void anEmptyCompilationHasNothingToBurn() {
        assertBlockedBy("burn.blocker.nothingToBurn", request(JobKind.DATA_DISC, BLANK_DVD, 0));
    }

    @Test
    void noDriveOrAnInaccessibleOneIsExplained() {
        assertBlockedBy("burn.blocker.noDrive", withDrive(Optional.empty()));
        DriveInfo locked = new DriveInfo(new DriveId("/dev/sr0"), "", "", ConnectionType.UNKNOWN, false);
        assertBlockedBy("burn.blocker.noPermissionLinux", withDrive(Optional.of(locked)));
        BurnPreCheck.Request windows = new BurnPreCheck.Request(
                JobKind.DATA_DISC,
                Optional.of(locked),
                Optional.of(BLANK_DVD),
                1,
                Optional.empty(),
                true,
                true,
                PowerSource.MAINS,
                Map.of(),
                OperatingSystem.WINDOWS);
        assertBlockedBy("burn.blocker.noPermission", windows);
    }

    @Test
    void theDiscMustBeThereAndWritable() {
        assertBlockedBy("burn.blocker.noDisc", request(JobKind.DATA_DISC, MediaInfo.noMedia(), 1));
        assertBlockedBy(
                "burn.blocker.notWritable",
                request(JobKind.DATA_DISC, new MediaInfo(MediaKind.DVD_ROM, MediaState.CLOSED, 0, 100), 1));
        assertBlockedBy(
                "burn.blocker.writeOnceUsed",
                request(JobKind.DATA_DISC, new MediaInfo(MediaKind.CD_R, MediaState.APPENDABLE, 100, 100), 1));
    }

    @Test
    void tooMuchDataIsBlockedWithTheExcess() {
        BurnPlan plan = BurnPreCheck.check(request(JobKind.DATA_DISC, BLANK_DVD, DVD + 1_500_000_000L));

        assertEquals(List.of(UserMessage.of("burn.blocker.tooBig", "1.5 GB", "4.7 GB")), plan.blockers());
    }

    @Test
    void aFullWorkFolderBlocksADataDisc() {
        BurnPreCheck.Request request = new BurnPreCheck.Request(
                JobKind.DATA_DISC,
                Optional.of(INTERNAL),
                Optional.of(BLANK_DVD),
                3_000_000_000L,
                Optional.of(new BurnPreCheck.WorkSpace(Path.of("work"), 1_000_000_000L)),
                true,
                true,
                PowerSource.MAINS,
                Map.of(),
                OperatingSystem.LINUX);

        assertEquals(
                List.of(UserMessage.of("burn.blocker.workFolderFull", "work", "3.0 GB", "1.0 GB")),
                BurnPreCheck.check(request).blockers());
    }

    @Test
    void missingToolsBlockWithTheirReasonsOnce() {
        UserMessage reason = UserMessage.of("feature.reason.toolMissing", "xorriso");
        Map<Feature, FeatureAvailability> features = Map.of(
                Feature.BURNING, new FeatureAvailability(Feature.BURNING, List.of(reason)),
                Feature.VERIFY, new FeatureAvailability(Feature.VERIFY, List.of(reason)));
        BurnPreCheck.Request request = new BurnPreCheck.Request(
                JobKind.BURN_ISO,
                Optional.of(INTERNAL),
                Optional.of(BLANK_DVD),
                1,
                Optional.empty(),
                true,
                true,
                PowerSource.MAINS,
                features,
                OperatingSystem.LINUX);

        assertEquals(List.of(reason), BurnPreCheck.check(request).blockers());
    }

    @Test
    void rewritableDiscsWithDataAreOverwrittenAndSomeErasedFirst() {
        BurnPlan dvdPlusRw = BurnPreCheck.check(
                request(JobKind.BURN_ISO, new MediaInfo(MediaKind.DVD_PLUS_RW, MediaState.APPENDABLE, DVD, 0), 1));
        BurnPlan cdRw = BurnPreCheck.check(
                request(JobKind.BURN_ISO, new MediaInfo(MediaKind.CD_RW, MediaState.CLOSED, 0, 700_000_000), 1));

        assertTrue(dvdPlusRw.canStart());
        assertTrue(dvdPlusRw.warnings().contains(UserMessage.of("burn.warning.overwrite")));
        assertFalse(dvdPlusRw.eraseFirst());
        assertTrue(cdRw.canStart(), cdRw.blockers().toString());
        assertTrue(cdRw.eraseFirst());
    }

    @Test
    void usbDrivesGetTheTipUntilItIsDismissedAndBatteryIsWarned() {
        DriveInfo usb = new DriveInfo(new DriveId("usb"), "Vendor", "Writer", ConnectionType.USB, true);
        BurnPlan withTip = BurnPreCheck.check(new BurnPreCheck.Request(
                JobKind.DATA_DISC,
                Optional.of(usb),
                Optional.of(BLANK_DVD),
                1,
                Optional.empty(),
                true,
                true,
                PowerSource.BATTERY,
                Map.of(),
                OperatingSystem.WINDOWS));
        BurnPlan dismissed = BurnPreCheck.check(new BurnPreCheck.Request(
                JobKind.DATA_DISC,
                Optional.of(usb),
                Optional.of(BLANK_DVD),
                1,
                Optional.empty(),
                true,
                false,
                PowerSource.UNKNOWN,
                Map.of(),
                OperatingSystem.WINDOWS));

        assertTrue(withTip.showUsbTip());
        assertEquals(
                List.of(
                        UserMessage.of("burn.warning.usbTip"),
                        UserMessage.of("burn.warning.battery"),
                        UserMessage.of("burn.note.sleep")),
                withTip.warnings());
        assertFalse(dismissed.showUsbTip());
        assertEquals(List.of(UserMessage.of("burn.note.sleep")), dismissed.warnings());
    }

    @Test
    void onlyRewritableDiscsWithDataCanBeErased() {
        assertTrue(BurnPreCheck.check(
                        request(JobKind.ERASE, new MediaInfo(MediaKind.DVD_PLUS_RW, MediaState.APPENDABLE, DVD, 0), 0))
                .canStart());
        assertBlockedBy("burn.blocker.notRewritable", request(JobKind.ERASE, BLANK_DVD, 0));
        assertBlockedBy(
                "burn.blocker.alreadyBlank",
                request(JobKind.ERASE, new MediaInfo(MediaKind.CD_RW, MediaState.BLANK, 700_000_000, 0), 0));
        assertFalse(
                BurnPreCheck.check(request(JobKind.ERASE, new MediaInfo(MediaKind.CD_RW, MediaState.CLOSED, 0, 1), 0))
                        .verify());
    }

    @Test
    void everyMessageThePreCheckUsesExists() {
        Messages messages = Messages.load(Locale.ROOT);
        List.of(
                        "burn.blocker.nothingToBurn",
                        "burn.blocker.noDrive",
                        "burn.blocker.noPermissionLinux",
                        "burn.blocker.noPermission",
                        "burn.blocker.noDisc",
                        "burn.blocker.notWritable",
                        "burn.blocker.writeOnceUsed",
                        "burn.blocker.notRewritable",
                        "burn.blocker.alreadyBlank",
                        "burn.warning.overwrite",
                        "burn.warning.usbTip",
                        "burn.warning.battery",
                        "burn.note.sleep",
                        "step.buildImage",
                        "step.blank",
                        "step.burnImage",
                        "step.verify")
                .forEach(key -> assertFalse(messages.format(UserMessage.of(key)).contains("''"), key));
        assertEquals(
                "This is 1.5 GB too big for the disc, which holds 4.7 GB. Remove some items or use a bigger disc.",
                messages.format(UserMessage.of("burn.blocker.tooBig", "1.5 GB", "4.7 GB")));
        assertTrue(messages.format(UserMessage.of("burn.blocker.workFolderFull", "/w", "3.0 GB", "1.0 GB"))
                .contains("/w"));
    }

    private static void assertBlockedBy(String key, BurnPreCheck.Request request) {
        BurnPlan plan = BurnPreCheck.check(request);
        assertFalse(plan.canStart());
        assertTrue(
                plan.blockers().stream().anyMatch(blocker -> blocker.key().equals(key)),
                key + " not in " + plan.blockers());
    }

    private static BurnPreCheck.Request withDrive(Optional<DriveInfo> drive) {
        return new BurnPreCheck.Request(
                JobKind.DATA_DISC,
                drive,
                Optional.of(BLANK_DVD),
                1,
                Optional.empty(),
                true,
                true,
                PowerSource.MAINS,
                Map.of(),
                OperatingSystem.LINUX);
    }

    private static BurnPreCheck.Request request(JobKind kind, MediaInfo media, long bytes) {
        return new BurnPreCheck.Request(
                kind,
                Optional.of(INTERNAL),
                Optional.of(media),
                bytes,
                Optional.empty(),
                true,
                true,
                PowerSource.MAINS,
                Map.of(),
                OperatingSystem.LINUX);
    }
}
