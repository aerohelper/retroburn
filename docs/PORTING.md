<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Porting RetroBurn to another operating system

| | |
|---|---|
| Status | Draft, written after the Linux and Windows platforms; reviewed before v0.1.0 |
| Background | [EXTENSIBILITY.md §1](EXTENSIBILITY.md), [LLD-engine-and-platform.md](design/LLD-engine-and-platform.md) |
| Worked examples | [LLD-linux-platform.md](design/LLD-linux-platform.md), [LLD-windows-platform.md](design/LLD-windows-platform.md) |

A port is one new package, `io.aerohelper.retroburn.platform.<os>`, plus a few
lines that select it. Everything else (projects, pipeline, UI, credits) is
shared. The two existing ports took opposite routes: Linux runs a command-line
tool (GNU xorriso) per operation; Windows runs a small helper that talks to
the OS's own burning API. Both are fine, as long as the rules below hold.

## 1. Rules that do not bend

- **Verify before you write.** Every command-line option, API call, constant
  and output format comes from the tool's or OS's documentation and is then
  checked on a real machine. Record the source and what you saw in your LLD.
  Never write an option from memory.
- **Capture real output** into `src/test/resources/tool-output/<tool>/` and
  test the parsers against it. Mask serial numbers and user names, and say so
  in that folder's `README.md`. Add new captures; never edit old ones.
- **Processes only through `tools.ProcessRunner`,** with an argument list. No
  shell, no `Runtime.exec`. The architecture tests enforce this.
- **No administrator or root rights, no network access, no telemetry.** If
  the OS needs a permission (Linux's `cdrom` group, for example), detect its
  absence and tell the user how to get it.
- **Nothing outside `platform.<os>` learns about your OS**, except the
  composition root in `app`. The architecture tests enforce this too.
- **Register every third-party program you use** in
  `src/main/resources/credits/components.json` with its licence text before
  the first commit that uses it, with `platforms` naming your OS.

## 2. Steps

1. **Write the LLD first** (`docs/design/LLD-<os>-platform.md`), with the
   same sections as the Linux and Windows ones: classes, how each operation
   is done, verified facts, hot-plug, sleep and power, errors and timeouts,
   folders, feature requirements, testing, and how to change it safely. If
   the approach is a real choice (a helper versus a tool, say), write an ADR
   and run a spike first, as ADR-0005 did for Windows.
2. **Add the OS** to `common.OperatingSystem` and to `PlatformDetector`'s
   `os.name` mapping, with a test.
3. **Create `platform.<os>`** and implement, in this order (each is usable
   on its own, and the simulated platform covers the rest meanwhile):
   - `AppPaths`: the OS's own folders for settings, data, logs, work and
     reels, from the environment, with documented fallbacks.
   - `ToolLocator`: where external tools live. User overrides from the
     settings come first (`OverridingToolLocator`), then the bundled folder
     and the search path (`PathSearchToolLocator`, with your OS's file
     extensions).
   - Feature requirements: which tools each `tools.Feature` needs on your OS.
   - `BurnEngine` (section 3 below) and `DiscImageBuilder`.
   - `DriveWatcher`: the cheapest change signal you can find (Linux reads a
     directory, Windows lists drive ids); `PollingDriveWatcher` may do.
   - `SleepInhibitor` and `PowerStatus`. A power status of "unknown" is
     allowed; it only means no battery warning.
4. **Wire it up** in `app.AppContext` (the `switch` on the OS) and add the
   package to `ArchitectureTest`'s list of platform packages.
5. **Add a CI runner** for the OS to `.github/workflows/ci.yml` if GitHub
   offers one, so the integration tests run on every change.

## 3. The engine contract

`BurnEngine` is the hard part. Its contract is in
[LLD-engine-and-platform.md §1.1](design/LLD-engine-and-platform.md); the
points that took the most care so far:

| Point | What the existing ports learned |
|---|---|
| Overwriting | A written DVD+RW must be overwritten without blanking. IMAPI2 refused until `ForceOverwrite` was set; xorriso's cdrecord emulation writes to "blank, overwritable, or appendable media". |
| Progress | Hold the fraction below 1.0 until the drive has finished: closing the track can take several seconds after the last byte. Do not invent percentages: erase estimates were meaningless on Windows, and the xorriso manual calls drives' blanking reports "quite unrealistic". |
| Cancel | Within 5 seconds where possible. A burn can only stop once the drive closes the partial track (7.5 s on Windows); say so in your LLD. Never kill a process the OS will keep running unseen (a Windows erase). |
| Verify | Compare byte for byte, reading exactly the image's length from the start of the disc. Windows needed a special call to read past the ISO 9660 volume's end; Linux streams the read-back through xorriso. |
| Errors | Map the OS's failures to typed exceptions with a user message key; keep the raw text and codes in the technical detail for "Show details". |

## 4. Tests

| Kind | Runs | Example |
|---|---|---|
| Parser and mapping tests against captured output | every OS, every build | `XorrisoParsersTest`, `Imapi2MappingTest` |
| Engine tests against a fake tool or helper replaying captures | every OS | `Imapi2EngineTest` with `FakeHelper` |
| Integration tests with the real tool, no drive needed | your OS, CI included | `XorrisoIntegrationTest` (a file as pseudo-drive), `WindowsHelperIntegrationTest` (images mounted and compared) |
| Real disc test, destroys the disc's content | opt-in only, never on CI | `WindowsRealDiscTest` with `RETROBURN_ERASE_TEST_DISC=yes` |
| Manual checks on real hardware | before the milestone is accepted | burn, verify, blank, hot-plug, battery warning, sleep during a long burn |

## 5. Before you open the pull request

- [ ] The LLD records every verified fact, with its source and the machine.
- [ ] Captures are committed, masked and described.
- [ ] `./mvnw verify` passes on your OS and on Linux and Windows CI.
- [ ] `reuse lint` passes; new tools are in `components.json`.
- [ ] The UI was tried end to end with a real drive and disc.
