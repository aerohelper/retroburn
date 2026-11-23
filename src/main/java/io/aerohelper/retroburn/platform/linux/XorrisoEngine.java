// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.linux;

import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.common.OperationCancelledException;
import io.aerohelper.retroburn.common.RetroBurnException;
import io.aerohelper.retroburn.common.ToolFailedException;
import io.aerohelper.retroburn.common.ToolMissingException;
import io.aerohelper.retroburn.common.UserMessage;
import io.aerohelper.retroburn.engine.BlankMode;
import io.aerohelper.retroburn.engine.BurnEngine;
import io.aerohelper.retroburn.engine.BurnEvent;
import io.aerohelper.retroburn.engine.BurnListener;
import io.aerohelper.retroburn.engine.BurnOptions;
import io.aerohelper.retroburn.engine.ConnectionType;
import io.aerohelper.retroburn.engine.DriveId;
import io.aerohelper.retroburn.engine.DriveInfo;
import io.aerohelper.retroburn.engine.MediaInfo;
import io.aerohelper.retroburn.engine.MediaState;
import io.aerohelper.retroburn.platform.linux.XorrisoDeviceListParser.ListedDrive;
import io.aerohelper.retroburn.tools.ExternalTool;
import io.aerohelper.retroburn.tools.ProcessRequest;
import io.aerohelper.retroburn.tools.ProcessResult;
import io.aerohelper.retroburn.tools.ProcessRunner;
import io.aerohelper.retroburn.tools.ToolLocator;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Drives, media, burning, blanking and verifying on Linux, through GNU xorriso (https://www.gnu.org/software/xorriso/,
 * GPL-3.0-or-later, by Thomas Schmitt and the libburnia project), run as a separate process.
 */
final class XorrisoEngine implements BurnEngine {

    /** Exit code from our "-return_with SORRY 32": a tolerated problem, such as "No drives found". */
    static final int EXIT_TOLERATED_PROBLEM = 32;

    /** The highest fraction shown before xorriso reports success. */
    private static final double BEFORE_DONE = 0.99;

    /** A drive spinning up a freshly inserted disc can take this long to answer. */
    private static final Duration QUERY_TIMEOUT = Duration.ofSeconds(60);

    /** Drives may stay silent for minutes while closing a track or erasing. */
    private static final Duration WRITE_IDLE = Duration.ofMinutes(10);

    /** Reading back produces data continuously; two minutes without any means the drive is stuck. */
    private static final Duration VERIFY_IDLE = Duration.ofMinutes(2);

    private static final long BLOCK_SIZE = 2048;

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

    @Override
    public void burnImage(DriveId drive, Path image, BurnOptions options, BurnListener listener, CancelToken cancel) {
        Consumer<String> output = line -> {
            listener.onEvent(new BurnEvent.LogLine(line));
            // 100 % written still leaves closing the track, so stay below done until xorriso exits.
            XorrisoProgressParser.parse(line)
                    .map(progress -> new BurnEvent.Progress(
                            Math.min(progress.fraction(), BEFORE_DONE), progress.speed(), progress.bufferFillPercent()))
                    .ifPresent(listener::onEvent);
        };
        ProcessResult result = runner.run(
                request(XorrisoCommandBuilder.burnImage(drive, image, options.closeMedia()))
                        .idleTimeout(WRITE_IDLE)
                        .onStdoutLine(output)
                        .onStderrLine(output)
                        .build(),
                cancel);
        if (!result.succeeded()) {
            throw failure("error.burnFailed", result);
        }
        listener.onEvent(BurnEvent.Progress.of(1.0));
    }

    @Override
    public void blank(DriveId drive, BlankMode mode, BurnListener listener, CancelToken cancel) {
        // Only "as_needed" leaves blank media alone.
        if (mode == BlankMode.FULL && readMedia(drive).state() == MediaState.BLANK) {
            listener.onEvent(new BurnEvent.LogLine("The disc is already blank."));
            listener.onEvent(BurnEvent.Progress.of(1.0));
            return;
        }
        Consumer<String> output = line -> listener.onEvent(new BurnEvent.LogLine(line));
        ProcessResult result = runner.run(
                request(XorrisoCommandBuilder.blank(drive, mode == BlankMode.FULL))
                        .idleTimeout(WRITE_IDLE)
                        .onStdoutLine(output)
                        .onStderrLine(output)
                        .build(),
                cancel);
        if (!result.succeeded()) {
            throw failure("error.blankFailed", result);
        }
        listener.onEvent(BurnEvent.Progress.of(1.0));
    }

    @Override
    public void verifyImage(DriveId drive, Path image, BurnListener listener, CancelToken cancel) {
        // Stopped at the first difference: reading the rest of the disc would only waste time.
        CancelToken stop = cancel.child();
        try (ImageComparison comparison = new ImageComparison(image, listener, stop::cancel)) {
            long blocks = Math.max(1, Math.ceilDiv(comparison.imageSize(), BLOCK_SIZE));
            ProcessResult result;
            try {
                result = runner.run(
                        request(XorrisoCommandBuilder.readBack(drive, blocks))
                                .idleTimeout(VERIFY_IDLE)
                                .stdoutTo(comparison)
                                .onStderrLine(line -> listener.onEvent(new BurnEvent.LogLine(line)))
                                .build(),
                        stop);
            } catch (OperationCancelledException e) {
                if (cancel.isCancelled() || comparison.mismatch().isEmpty()) {
                    throw e;
                }
                throw mismatch(
                        "First difference at byte " + comparison.mismatch().getAsLong());
            }
            if (comparison.complete()) {
                listener.onEvent(BurnEvent.Progress.of(1.0));
                return;
            }
            // Nothing at all came back (no drive, no disc, stuck drive): a read problem, not a bad copy.
            if (result.timedOut() || (!result.succeeded() && comparison.compared() == 0)) {
                throw failure("error.verifyFailed", result);
            }
            // Unreadable blocks are left out of the copy, so the disc came back short.
            throw mismatch("The disc ends at byte " + comparison.compared() + " of " + comparison.imageSize() + "\n"
                    + result.technicalDetail());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + image, e);
        }
    }

    @Override
    public void eject(DriveId drive) {
        ProcessResult result = run(XorrisoCommandBuilder.eject(drive));
        if (!result.succeeded()) {
            throw failure("error.ejectFailed", result);
        }
    }

    private static RetroBurnException mismatch(String detail) {
        return new RetroBurnException(UserMessage.of("error.verifyMismatch"), detail);
    }

    private ProcessResult run(List<String> arguments) {
        return runner.run(request(arguments).totalTimeout(QUERY_TIMEOUT).build(), CancelToken.none());
    }

    private ProcessRequest.Builder request(List<String> arguments) {
        Path xorriso = locator.locate(ExternalTool.XORRISO)
                .orElseThrow(() -> new ToolMissingException(ExternalTool.XORRISO.displayName()));
        return ProcessRequest.builder(xorriso, arguments);
    }

    private static ToolFailedException failure(String messageKey, ProcessResult result) {
        String reason = XorrisoErrorParser.mainProblem(result.outputTail()).orElse("");
        return new ToolFailedException(UserMessage.of(messageKey, reason), result.technicalDetail());
    }
}
