<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# LLD — Engine contracts and platform services (`engine`, `platform`)

| | |
|---|---|
| Status | Implemented in the core milestone (M2); writing added in the Windows engine milestone (M3) |
| Packages | `io.aerohelper.retroburn.engine`, `.platform`, `.platform.simulated` |
| Related | [HLD §4](../architecture/HLD.md), [LLD-linux-platform.md](LLD-linux-platform.md), [EXTENSIBILITY.md](../EXTENSIBILITY.md) |

## 1. Engine model (`engine`, depends only on `common`)

| Type | Meaning |
|---|---|
| `DriveId` (record) | Opaque drive address: `/dev/sr0` on Linux, the IMAPI2 unique id on Windows. |
| `ConnectionType` (enum) | `USB`, `OTHER` (known not to be USB), `UNKNOWN`. The UI shows a badge only for `USB`. |
| `DriveInfo` (record) | Id, vendor, product, connection type. |
| `MediaFamily` (enum) | `CD`, `DVD`, `BD`. |
| `MediaKind` (enum) | Inserted medium (`CD_R`, `DVD_PLUS_RW`, `BD_RE`, `NONE`, `UNKNOWN`, …) with family, writable and rewritable flags. |
| `MediaState` (enum) | `BLANK`, `APPENDABLE`, `CLOSED`, `NO_MEDIA`, `UNKNOWN`. |
| `MediaInfo` (record) | Kind, state, free bytes, used bytes. |
| `DiscEntry` (record) | A path on the disc and the file or folder that supplies it. A folder brings its whole subtree. |
| `DiscTree` (interface) | `Stream<DiscEntry> entries()` — streamed, never copied, so very large compilations do not duplicate data in memory. `DiscTree.of(list)` for small trees. |
| `ImageOptions` (record) | Volume label (more options as features arrive). |
| `BurnEvent` (sealed) | `Progress(fraction, speed, bufferFill)` and `LogLine(text)`. |
| `BurnListener` | Receives `BurnEvent`s. |
| `BurnEngine` | `listDrives()`, `readMedia(drive)`, `burnImage(drive, image, options, listener, cancel)`, `blank(drive, mode, listener, cancel)`, `verifyImage(drive, image, listener, cancel)`, `eject(drive)`; section 1.1. |
| `BurnOptions` (record) | `closeMedia`: finalise the disc so nothing more can be added. |
| `BlankMode` (enum) | `QUICK` (makes the disc reusable; fast), `FULL` (erases every sector; can take as long as writing the whole disc). |
| `DiscImageBuilder` | `buildDataImage(tree, options, target, listener, cancel)`. |

Disc paths start with `/`, use `/` as separator, and must not contain empty
segments, `.` or `..`, or newline characters; `DiscEntry` rejects them.

### 1.1 Writing contract

Added in the Windows engine milestone (M3), on every platform at once.

| Method | Contract |
|---|---|
| `burnImage` | Writes the image file to the disc as one data track from the start of the writable area. The caller has already checked that the medium is writable and large enough; the engine still fails clearly if not. The disc is not blanked first: a used CD-RW or DVD-RW needs `blank` beforehand, while DVD+RW, DVD-RAM and BD-RE are simply overwritten. Progress is the fraction of the image written, held at 0.99 until the drive has finished closing the track. |
| `blank` | Makes a rewritable disc ready for writing from scratch. A blank disc is left alone. Windows cannot cancel an erase once it has started, so cancelling is only honoured before it starts there; the UI offers no cancel button while blanking. No progress is reported until it is done (drives' estimates are meaningless), so the UI shows an erase without a percentage. |
| `verifyImage` | Reads back as many bytes as the image has, starting at the beginning of the disc, and compares them byte for byte. A difference, or a disc shorter than the image, fails with `error.verifyMismatch`; the first differing byte offset is in the technical detail. If nothing at all can be read, it fails with the platform's read error instead. Progress is the fraction compared. |
| `eject` | Opens the tray, or does nothing where that is impossible (a file pretending to be a drive in tests). |

Differences from the HLD sketch (§4.2), decided here: `burnImage` takes a
`Path` (no `ImageSource` type is needed yet); `verifyImage` returns nothing
and throws on a mismatch instead of returning a `VerifyReport`, because the
only outcome the UI acts on is "matches" or "does not, at byte N"; `eject` is
added. `burnTree` (writing a `DiscTree` without an image file first) comes
with the data disc milestone (M4).

Every long operation honours its `CancelToken` within 5 seconds, with two
exceptions: a started Windows erase cannot be cancelled, and a burn ends only
once the drive has closed the partial track (7.5 s measured on Windows with a
DVD+RW), so the UI shows "Stopping…" until the engine returns. A cancelled
burn leaves an incomplete disc: rewritable ones can be burned or blanked
again, write-once ones are lost; the UI says so before cancelling.

## 2. Platform services (`platform`)

```java
public interface PlatformServices {
    OperatingSystem operatingSystem();
    String displayName();
    AppPaths appPaths();
    ToolLocator toolLocator();
    Map<Feature, List<Requirement>> featureRequirements();
    BurnEngine burnEngine();
    DiscImageBuilder discImageBuilder();
    DriveWatcher createDriveWatcher();
    SleepInhibitor sleepInhibitor();
    PowerStatus powerStatus();
}
```

| Type | Responsibility |
|---|---|
| `AppPaths` (record) | Settings, data, logs, work and reels folders. Created by the platform; `ensureExists()` creates them. Never hard-coded elsewhere. |
| `DriveWatcher` | Hot-plug: listeners get the new drive list when it changes; `pause()`/`resume()` around burns; `start()`/`close()`. The platform creates a new one per caller. |
| `PollingDriveWatcher` | OS-neutral implementation that polls a cheap "drive signature" supplier every 2 s and asks for full drive details only when the signature changes. Paused polls do nothing. |
| `SleepInhibitor` | `acquire(reason)` returns a `SleepInhibition` (`AutoCloseable`) or throws `SleepInhibitionUnavailableException`. |
| `PowerStatus` | `PowerSource current()` — `MAINS`, `BATTERY` or `UNKNOWN`. Unknown means "do not warn". |

### Simulated platform

`SimulatedPlatform` gains a fake drive (`SimulatedBurnEngine`: one USB DVD
writer with a DVD+RW that starts blank; burning makes it appendable and
remembers the image's size, blanking makes it blank again, verifying compares
the image's size with the remembered one, and every long operation reports
progress in ten steps and honours cancellation), an image builder that reports progress and writes
a small text listing instead of a real image, a no-op sleep inhibitor,
`PowerSource.UNKNOWN`, no tool requirements (every feature available), and
`AppPaths` given by its creator. It is used on Windows until the Windows
platform exists, and in tests.

## 3. Platform selection

`AppContext.createDefault()`:

| Detected OS | Platform |
|---|---|
| Linux | `LinuxPlatform` |
| Windows | `WindowsPlatform` ([LLD-windows-platform.md](LLD-windows-platform.md)) |
| Other | `SimulatedPlatform` with folders under the user's home (`.retroburn-simulated`) |

## 4. How to change this safely

- New engine methods are added to the interface **and** to every platform,
  including the simulated one, in the same change.
- Engine types stay free of OS concepts; anything OS-specific belongs in
  `platform.<os>`.
- `DriveWatcher` implementations must never poll while paused: polling can
  disturb a drive that is writing.
- A `SleepInhibition` must be released on every path; use try-with-resources.
