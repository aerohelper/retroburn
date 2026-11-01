// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.project;

import io.aerohelper.retroburn.engine.BlankMode;
import io.aerohelper.retroburn.engine.BurnEngine;
import io.aerohelper.retroburn.engine.DiscImageBuilder;
import io.aerohelper.retroburn.engine.DiscTree;
import io.aerohelper.retroburn.engine.DriveId;
import io.aerohelper.retroburn.pipeline.Pipeline;
import io.aerohelper.retroburn.pipeline.PipelineStep;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the pipeline for each data-burning job. Weights follow the bytes each step moves; writing
 * counts double because drives write slower than disks and drives read. See {@code
 * docs/design/LLD-data-burning.md} section 4.
 */
public final class BurnJobFactory {

    /** The data disc's image, inside the job's work folder (deleted with it). */
    static final String IMAGE_NAME = "disc.iso";

    private final BurnEngine engine;
    private final DiscImageBuilder imageBuilder;

    public BurnJobFactory(BurnEngine engine, DiscImageBuilder imageBuilder) {
        this.engine = engine;
        this.imageBuilder = imageBuilder;
    }

    /** Files and folders: build the image in the work folder, then burn and verify it. */
    public Pipeline dataDisc(BurnPlan plan, DiscTree tree, VolumeLabel label) {
        requireKind(plan, JobKind.DATA_DISC);
        BurnSteps.ImageFile image = context -> context.workFolder().resolve(IMAGE_NAME);
        List<PipelineStep> steps = new ArrayList<>();
        steps.add(new BurnSteps.BuildImage(imageBuilder, tree, label, image, bytes(plan)));
        steps.addAll(writeSteps(plan, image));
        return new Pipeline(steps);
    }

    /** An existing image: burn and verify it. */
    public Pipeline burnIso(BurnPlan plan, Path isoFile) {
        requireKind(plan, JobKind.BURN_ISO);
        return new Pipeline(writeSteps(plan, context -> isoFile));
    }

    /** Erase a rewritable disc. */
    public Pipeline erase(BurnPlan plan, BlankMode mode) {
        requireKind(plan, JobKind.ERASE);
        return Pipeline.of(new BurnSteps.Blank(engine, drive(plan), mode, 1));
    }

    /** Files and folders to an image file the user chose; no drive involved. */
    public Pipeline createIso(DiscTree tree, VolumeLabel label, Path target, long estimatedBytes) {
        return Pipeline.of(
                new BurnSteps.BuildImage(imageBuilder, tree, label, context -> target, Math.max(1, estimatedBytes)));
    }

    /** Erase first when needed, burn the image, verify it: the end of every image-based job. */
    List<PipelineStep> writeSteps(BurnPlan plan, BurnSteps.ImageFile image) {
        DriveId drive = drive(plan);
        double bytes = bytes(plan);
        List<PipelineStep> steps = new ArrayList<>();
        if (plan.eraseFirst()) {
            steps.add(new BurnSteps.Blank(engine, drive, BlankMode.QUICK, bytes * 0.05));
        }
        steps.add(new BurnSteps.BurnImage(engine, drive, image, bytes * 2));
        if (plan.verify()) {
            steps.add(new BurnSteps.VerifyImage(engine, drive, image, bytes));
        }
        return steps;
    }

    static DriveId drive(BurnPlan plan) {
        if (!plan.canStart()) {
            throw new IllegalArgumentException("The plan has blockers: " + plan.blockers());
        }
        return plan.drive().orElseThrow().id();
    }

    private static double bytes(BurnPlan plan) {
        return Math.max(1, plan.bytesToWrite());
    }

    private static void requireKind(BurnPlan plan, JobKind expected) {
        if (plan.kind() != expected) {
            throw new IllegalArgumentException("Expected a " + expected + " plan, got " + plan.kind());
        }
    }
}
