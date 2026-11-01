<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# LLD — Data burning (`project`, shared burn screens)

| | |
|---|---|
| Status | Designed for the data-burning UI milestone (M4); part 1 (models, checks, steps) first, part 2 (screens) after |
| Packages | `io.aerohelper.retroburn.project` (models, checks, steps, job), `.settings`, `.ui`, `.app` |
| Related | [ADR-0013](../adr/0013-data-discs-burned-from-an-image.md), [HLD §4.3, §5, §6](../architecture/HLD.md), [LLD-engine-and-platform.md](LLD-engine-and-platform.md), [LLD-pipeline.md](LLD-pipeline.md), [UX_SPEC.md](../ux/UX_SPEC.md) |

## 1. Scope

Four home-screen cards become usable, on Linux and Windows:

| Card | What it does |
|---|---|
| Data disc | Files and folders → image in the work folder → burn → verify |
| Burn ISO | An existing `.iso` → burn → verify against it |
| Create ISO | Files and folders → `.iso` where the user chooses, no drive needed |
| Erase disc | Quick or full erase of a rewritable disc (reached from the drive bar, not a card) |

All of them share the drive bar, the confirmation dialog, the progress view
and the result view. Saving projects as `.rbproj` is not part of this
milestone (it is optional for v0.1; it will come with its own data model
entry).

## 2. Models (`project`, no JavaFX)

| Type | Responsibility |
|---|---|
| `VolumeLabel` (record) | A label that reads the same on Linux and Windows (section 2.1). `VolumeLabel.sanitize(text)` never fails. |
| `DataCompilation` | The items of a data disc: an ordered list of `DiscEntry`s at the disc's root, one per file or folder added. Adding a name that is already used gets ` (2)`, ` (3)`… before the extension. Implements `DiscTree`. Immutable; every change returns a new one. |
| `TreeSizeScanner` | Walks the sources in the background and returns a `TreeSize` (bytes, files, folders, unreadable paths), checking a `CancelToken` every entry. It never holds the file list, so 100,000 files cost no memory beyond the walk. Symbolic links are counted as the link itself, not followed. |
| `ImageSizeEstimate` | Bytes the image will take: every file rounded up to 2048-byte blocks, 2560 bytes per folder and file entry (a 2048-byte UDF file entry plus ISO 9660 and Joliet directory records), plus a fixed 2 MiB for volume descriptors, path tables and the UDF structures (measured with one file: 1.1 MiB on Windows, 0.4 MiB on Linux; the margin keeps the estimate on the safe side). The exact size is checked again once the image exists. Measured with 2,000 small files plus 1 MiB: IMAPI2FS image 10.8 MB, xorriso image 6.0 MB, estimate 12.4 MB. Linux images carry no UDF, so there the estimate is about 2 KB per item too high: a nearly full disc with very many small files can be refused although it would fit. |
| `DiscCapacity` | Bytes a burn can use on the inserted disc: `freeBytes` when blank; `freeBytes + usedBytes` for a rewritable disc with data, which will be overwritten or erased. |
| `BurnPlan` (record) | What the confirmation dialog shows and the job needs: project kind, drive, media, bytes to write, whether the disc will be erased first, verify on/off, the warnings and the blockers. |
| `BurnPreCheck` | Turns (project, drive, media, settings, power, tools) into a `BurnPlan`; section 3. Pure logic, fully unit-tested. |
| `BurnJobFactory` | Builds the `Pipeline` for a plan from the steps in `BurnSteps` (package-private); section 4. |

### 2.1 Volume label rules

Measured on Windows 11 with IMAPI2FS images (ISO 9660 + Joliet + UDF):
Windows shows the UDF label, which kept 32 characters and mixed case
(`Holiday 2026` stayed as typed). Images built on Linux have no UDF, and the
Joliet volume identifier holds 16 UCS-2 characters. So, to read the same
everywhere:

- at most **16 characters**;
- letters `A–Z`, `a–z`, digits, space, `_` and `-`; anything else becomes `_`;
- leading and trailing spaces are dropped; an empty result becomes
  `RETROBURN`.

The field shows the sanitised value as the user types and says why a
character changed. *To confirm in CI:* xorriso's `-toc` volume id and the
Joliet name for `Holiday 2026`.

## 3. Pre-checks

Run when the user presses the big action button, and again whenever the drive
bar reports a change while the confirmation dialog is open.

| Check | Result |
|---|---|
| No drive, or the chosen one has gone | Blocker "No disc drive found…" |
| Drive not accessible | Blocker with the permission hint (Linux: `cdrom` group) |
| No disc | Blocker "Insert a disc" |
| Disc not writable (pressed CD-ROM, DVD-ROM, BD-ROM, unknown) | Blocker "This disc can't be written" |
| Write-once disc that is not blank | Blocker "This disc already has data on it. Use a blank disc." (no multisession, ADR-0013) |
| Bytes to write > `DiscCapacity` | Blocker "This is {excess} too big for the disc…" |
| Work folder free space < image estimate (data disc only) | Blocker naming the folder and both sizes; the work folder can be changed in the settings |
| `Feature.BURNING` unavailable (`Feature.VERIFY` when verify is on) | Blocker with the reasons from `FeatureGate` |
| Rewritable disc with data | Warning "The disc's current content will be erased"; CD-RW and DVD-RW also get a quick erase step first (section 4) |
| Drive on USB and the USB tip not dismissed | Warning: the USB tip, with "Don't show again" |
| `PowerStatus` reports battery | Warning: "Your laptop is running on battery…" |
| Always | Note: the sleep note |

Blockers disable the confirm button and are listed with what to do; warnings
are shown and do not stop the burn. Every text is in `messages.properties`.

## 4. Pipelines

| Project | Steps (weight) |
|---|---|
| Data disc | `BuildImageStep` (1 per byte) → [`BlankStep` quick (fixed, 5 % of the burn)] → `BurnImageStep` (2 per byte) → [`VerifyImageStep` (1 per byte)] |
| Burn ISO | [`BlankStep`] → `BurnImageStep` → [`VerifyImageStep`] |
| Create ISO | `BuildImageStep` writing to the chosen file |
| Erase disc | `BlankStep` with the chosen mode |

Steps in brackets are present only when the plan needs them. Burning,
blanking and verifying need the system awake; building does not. The image
of a data disc lives in the job's work folder and goes with it (deleted on
success and cancel, kept on failure until the next start, per
LLD-pipeline).

| Step | Does |
|---|---|
| `BuildImageStep` | `DiscImageBuilder.buildDataImage(tree, label, target)`. |
| `BlankStep` | `BurnEngine.blank(drive, mode)`. |
| `BurnImageStep` | Reads the media again; fails with `error.discTooSmall` if the image no longer fits (the estimate was low or the disc changed); then `BurnEngine.burnImage(drive, image, closeMedia = true)`. |
| `VerifyImageStep` | `BurnEngine.verifyImage(drive, image)`. |

### 4.1 The job around the pipeline

`BurnJob` (in `project`) runs a plan's pipeline on the `pipeline-runner`
thread:

1. `DriveWatcher.pause()`, so nothing polls the drive while it writes.
2. `PipelineRunner.run(...)` with the settings' sleep and temporary-file
   options.
3. `DriveWatcher.resume()` in `finally`.
4. On failure, `BurnEngine.listDrives()` once: if the drive is missing, the
   result is **drive lost** (the U4-style message in the UX spec: "The drive
   was disconnected or lost power…") with the original error kept in "Show
   details"; otherwise the failure as reported.

Result kinds: succeeded, succeeded without verify, verify failed (the
`error.verifyMismatch` failure), failed, drive lost, cancelled.

## 5. Settings added

| Key | Type | Default | Meaning |
|---|---|---|---|
| `burn.verify` | boolean | `true` | Add the verify step; the confirmation dialog can change it for one burn. |
| `burn.showUsbTip` | boolean | `true` | Show the USB tip; "Don't show again" sets it to `false`. |

Both are added to `docs/DATA_MODEL.md`'s `settings.properties` table; the
schema version stays 1 (new keys with defaults are compatible).

## 6. Screens (part 2, `ui`)

Views are thin JavaFX classes over the models above; anything that can be
tested without a window lives in the models. Long work runs on background
threads and comes back through `Platform.runLater`.

| View | Contents |
|---|---|
| `DriveBar` | Drive picker (vendor, product, then the drive letter or device from `DriveInfo.location`), USB badge, media kind and state (`MediaText`, for example "DVD+RW · has data · 4.7 GB usable"), refresh, "Erase disc…" (shown only for rewritable media), empty and permission states. Fed by `DriveWatcher`; reads media on selection, on window focus and on refresh, and drops a reply that a newer read has overtaken. |
| `CapacityBar` | Below the view heading: under, near full ≥ 95 %, over, calculating. The state is in the text as well as the colour. Create ISO shows the size only. |
| `DataDiscView` | Volume label field with a hint showing the cleaned label, item table (name on the disc and source path; virtualised; Delete removes), Add files, Add folder, Remove, drag and drop, total versus capacity, Burn and Create ISO buttons. |
| `BurnIsoView` | Image picker with size and capacity check; Burn button. |
| `ConfirmBurnDialog` | The `BurnPlan`: summary, blockers, warnings, verify checkbox (changing it re-plans), USB tip with "Don't show again", Burn (never the default button) and Back (the default). For an erase it is the erase confirmation: "Erase this disc? All data on it will be deleted." with Quick or Full; there is no separate erase dialog. |
| `ProgressView` | Step list with ticks, overall percentage, speed and buffer when the engine reports them, elapsed time, Cancel with the cancel warning, "Stopping…" while a cancel completes. |
| `ResultView` | One of the result kinds from section 4.1, "Show details" with the technical detail and log tail, Back to home. |
| `BurnFlow` | The shared path every job takes: pre-check in the background (media, power, work-folder space), the confirm dialog, saving the USB-tip choice, `BurnJob` on the jobs thread, then progress and result. Create ISO uses the same progress and result without a drive. |
| `Navigation` (`ui`), implemented by `MainWindow` (`app`) | Home, open a project screen, show progress or result on top of it, back to the project, the owner window. The project screen is kept while progress and result are shown, so Back returns to the same compilation. |
| `UiServices` (`ui`) | What the views need from `app`, passed in one record: messages, platform, settings store, pipeline runner, current tool features, work-folder root, a background executor and the single jobs thread. |

## 7. Errors

All engine and step errors arrive as `RetroBurnException`s with a user message
(section 8 of each platform LLD). The result view shows the message; "Show
details" shows the technical detail. Unexpected exceptions are wrapped by the
pipeline runner. Nothing waits without a bound (engine timeouts).

## 8. Testing

- Unit tests (every OS): `VolumeLabel`, `DataCompilation` (naming
  collisions), `TreeSizeScanner` (synthetic trees, cancel, unreadable
  entries), `ImageSizeEstimate` against real image sizes, `DiscCapacity`,
  every row of the pre-check table, the pipelines `BurnJobFactory` builds,
  and `BurnJob` with the simulated platform (success, verify mismatch,
  cancel, drive lost).
- Benchmark-style test: scanning 100,000 synthetic files stays within a time
  and memory budget.
- Integration: the existing image, burn and verify tests per platform.
- Manual: `docs/MANUAL_TEST.md` (written in part 2), on both systems with the
  owner's USB drive.

## 9. How to change this safely

- Every new pre-check gets a row in section 3, a message key and a unit test.
- Steps stay thin: they call the engine and report; logic goes into the
  models.
- Keep JavaFX out of `project` (`ArchitectureTest` enforces it).
