<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# LLD — Engine contracts and platform services (`engine`, `platform`)

| | |
|---|---|
| Status | Implemented in the core milestone (M2); extended per milestone |
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
| `BurnEngine` | `listDrives()`, `readMedia(drive)`. Burning, blanking and verifying are added with the data-burning milestone. |
| `DiscImageBuilder` | `buildDataImage(tree, options, target, listener, cancel)`. |

Disc paths start with `/`, use `/` as separator, and must not contain empty
segments, `.` or `..`, or newline characters; `DiscEntry` rejects them.

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
writer with a blank DVD+RW), an image builder that reports progress and writes
a small text listing instead of a real image, a no-op sleep inhibitor,
`PowerSource.UNKNOWN`, no tool requirements (every feature available), and
`AppPaths` given by its creator. It is used on Windows until the Windows
platform exists, and in tests.

## 3. Platform selection

`AppContext.createDefault()`:

| Detected OS | Platform |
|---|---|
| Linux | `LinuxPlatform` |
| Windows, other | `SimulatedPlatform` with folders under the user's home (`.retroburn-simulated`) — replaced by `WindowsPlatform` in the Windows engine milestone |

## 4. How to change this safely

- New engine methods are added to the interface **and** to every platform,
  including the simulated one, in the same change.
- Engine types stay free of OS concepts; anything OS-specific belongs in
  `platform.<os>`.
- `DriveWatcher` implementations must never poll while paused: polling can
  disturb a drive that is writing.
- A `SleepInhibition` must be released on every path; use try-with-resources.
