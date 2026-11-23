// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.linux;

import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.common.ToolFailedException;
import io.aerohelper.retroburn.common.ToolMissingException;
import io.aerohelper.retroburn.common.UserMessage;
import io.aerohelper.retroburn.engine.BurnEngine;
import io.aerohelper.retroburn.engine.ConnectionType;
import io.aerohelper.retroburn.engine.DriveId;
import io.aerohelper.retroburn.engine.DriveInfo;
import io.aerohelper.retroburn.engine.MediaInfo;
import io.aerohelper.retroburn.platform.linux.XorrisoDeviceListParser.ListedDrive;
import io.aerohelper.retroburn.tools.ExternalTool;
import io.aerohelper.retroburn.tools.ProcessRequest;
import io.aerohelper.retroburn.tools.ProcessResult;
import io.aerohelper.retroburn.tools.ProcessRunner;
import io.aerohelper.retroburn.tools.ToolLocator;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Drive and media information on Linux, through GNU xorriso (https://www.gnu.org/software/xorriso/,
 * GPL-3.0-or-later, by Thomas Schmitt and the libburnia project), run as a separate process.
 */
final class XorrisoEngine implements BurnEngine {

    /** Exit code from our "-return_with SORRY 32": a tolerated problem, such as "No drives found". */
    static final int EXIT_TOLERATED_PROBLEM = 32;

    /** A drive spinning up a freshly inserted disc can take this long to answer. */
    private static final Duration QUERY_TIMEOUT = Duration.ofSeconds(60);

    private final ProcessRunner runner;
    private final ToolLocator locator;
    private final SysfsOpticalDrives sysfs;

    XorrisoEngine(ProcessRunner runner, ToolLocator locator, SysfsOpticalDrives sysfs) {
        this.runner = runner;
        this.locator = locator;
        this.sysfs = sysfs;
    }

    @Override
    public List<DriveInfo> listDrives() {
        ProcessResult result = run(XorrisoCommandBuilder.listDrives());
        boolean noDrives = XorrisoDeviceListParser.reportsNoDrives(result.outputTail());
        if (!result.succeeded() && !(noDrives && result.exitCode() == EXIT_TOLERATED_PROBLEM)) {
            throw failure("error.driveListFailed", result);
        }
        return combine(sysfs.deviceNames(), XorrisoDeviceListParser.parse(result.outputTail()));
    }

    /**
     * Merges the kernel's list with xorriso's. A drive the kernel knows but xorriso cannot open is one
     * the user lacks permission for, so it is listed as not accessible.
     */
    List<DriveInfo> combine(List<String> kernelNames, List<ListedDrive> openable) {
        Map<String, ListedDrive> byAddress = new LinkedHashMap<>();
        openable.forEach(drive -> byAddress.put(drive.address(), drive));
        List<DriveInfo> drives = new ArrayList<>();
        for (String name : kernelNames) {
            String address = SysfsOpticalDrives.devicePath(name);
            ListedDrive listed = byAddress.remove(address);
            drives.add(
                    listed == null
                            ? new DriveInfo(new DriveId(address), "", "", sysfs.connectionOf(name), false)
                            : new DriveInfo(
                                    new DriveId(address),
                                    listed.vendor(),
                                    listed.product(),
                                    sysfs.connectionOf(name),
                                    true));
        }
        // Drives xorriso found under another name (unusual setups) are still offered.
        for (ListedDrive listed : byAddress.values()) {
            drives.add(new DriveInfo(
                    new DriveId(listed.address()), listed.vendor(), listed.product(), ConnectionType.UNKNOWN, true));
        }
        return List.copyOf(drives);
    }

    @Override
    public MediaInfo readMedia(DriveId drive) {
        ProcessResult result = run(XorrisoCommandBuilder.readMedia(drive));
        if (!result.succeeded()) {
            throw failure("error.mediaReadFailed", result);
        }
        return XorrisoMediaInfoParser.parse(result.outputTail());
    }

    private ProcessResult run(List<String> arguments) {
        Path xorriso = locator.locate(ExternalTool.XORRISO)
                .orElseThrow(() -> new ToolMissingException(ExternalTool.XORRISO.displayName()));
        ProcessRequest request = ProcessRequest.builder(xorriso, arguments)
                .totalTimeout(QUERY_TIMEOUT)
                .build();
        return runner.run(request, CancelToken.none());
    }

    private static ToolFailedException failure(String messageKey, ProcessResult result) {
        String reason = XorrisoErrorParser.mainProblem(result.outputTail()).orElse("");
        return new ToolFailedException(UserMessage.of(messageKey, reason), result.technicalDetail());
    }
}
