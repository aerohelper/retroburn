<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# 0005 — Reach IMAPI2 through a PowerShell helper

- Status: Accepted (confirmed by the Windows engine spike, 2026-11-01)
- Date: 2026-10-31, updated 2026-11-01
- Deciders: project owner

## Context and problem statement

On Windows, RetroBurn burns through the Image Mastering API v2 (IMAPI2 and
IMAPI2FS), which is exposed as COM objects. Java has no built-in COM support.
How should Java talk to IMAPI2?

## Decision drivers

- Reliable progress events during long burns.
- Readability: a maintainer without COM/JNI experience can follow the code.
- Testability: the Java side can be tested without a drive.
- No administrator rights.
- Consistency with the Linux "external process" model (ADR-0004).
- Crash isolation and guaranteed release of the sleep-inhibition request.

## Considered options

1. **PowerShell helper**: a bundled, readable PowerShell 5.1 script (ships with
   Windows 10/11) that uses the IMAPI2 COM objects and prints events as JSON
   Lines on stdout.
2. **JNA COM bindings** in-process.
3. A small prebuilt helper (C#/C++), rejected up front: adds a second
   language toolchain and a binary to build, sign and audit.

## Decision outcome

Chosen option: **option 1, PowerShell helper**, with one refinement found by
the spike: the helper's work is done by **C# source shipped next to a short
script and compiled at run time by `Add-Type`**, which uses the C# compiler
that is part of the .NET Framework in Windows. The script only starts it. No
binary is built, shipped or signed by us, so the reason for rejecting option 3
does not apply; both files stay plain text a maintainer can read.

Why C# is needed: IMAPI2 delivers its events on its own worker thread (a
multi-threaded apartment thread), not on the script's thread. A PowerShell
script block cannot run there and fails with "There is no Runspace available
to run scripts in this thread"; the COM caller swallows that failure, so
events silently disappear. Sinks written in C# run fine on that thread and
write their JSON lines themselves.

### Spike results

Windows 11 Pro 10.0.26200, Windows PowerShell 5.1, standard user, USB drive
"Slimtype eBAU108", DVD+RW.

| Required by this ADR | Result |
|---|---|
| Progress events from the IMAPI2 data writer reach the helper | **Yes, from C#.** 104 callbacks in one erase and burn: erase updates every second; write updates every ~0.5 s with sectors written 0…15967 of 15968 and the stages initialising → calibrating → writing → finalising → verifying → completed. |
| IMAPI2FS image written to a file without a drive | **Yes.** ISO 9660 + Joliet + UDF image built from a tree with spaces, `=`, accented and Devanagari names; mounted and compared file by file: identical. |
| Device add/remove notification, or polling adequate | Notifications did not arrive in the script (tested before the threading cause was known). **Polling is adequate**: enumerating `MsftDiscMaster2` noticed a re-plugged drive within one second. Polling is used. |
| `SetThreadExecutionState` held by the helper | **Yes**, through a one-line C# declaration. |
| Standard user, default policies, no weakening of system policy | **Yes**: `powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File <script>` sets the policy for that process only. A policy set by Group Policy (MachinePolicy/UserPolicy) overrides this; the helper then refuses to start and RetroBurn explains why. |
| Antivirus does not block it | Microsoft Defender (default settings) did not interfere. Other products are untested; tracked in RISKS.md. |

Further facts verified during the spike (details in the Windows LLD): erase
and burn of a DVD+RW, read-back of the whole disc matching the image byte for
byte, USB detection through the storage bus type, and power status.

Protocol:

- Java starts PowerShell by absolute path with an argument list naming the
  bundled script. No value is ever interpolated into script text.
- The request (operation, drive id, paths, options) is sent as **one JSON
  document on the first line of stdin**. Long-running operations keep stdin
  open; closing it asks the helper to stop.
- The helper writes one JSON object per line to stdout. A pure Java parser maps
  them to `BurnEvent`s and typed exceptions and is tested with captured real
  output.

### Consequences

- Good: mirrors the Linux model; a helper crash cannot crash the app; if the
  helper dies, its execution-state request dies with it.
- Good: script and C# are plain text a maintainer can read and debug.
- Good: JNA is not needed, so no new dependency.
- Bad: PowerShell start-up plus compiling the C# takes about 1.3 s per
  operation. Acceptable for burns; hot-plug uses one long-running helper
  instead of starting one per poll.
- Bad: `Add-Type` is unavailable in PowerShell's Constrained Language Mode
  (enforced by some application-control policies). The helper then fails its
  start-up check and burning features are disabled with an explanation.
- Bad: some security products scrutinise PowerShell; tracked in RISKS.md.

## More information

- IMAPI2 reference: Microsoft Learn, "Image Mastering API" documentation.
- Interface GUIDs, DISPIDs and enum values were read from the type libraries
  in `imapi2.dll` and `imapi2fs.dll` with `tools/windows/dump-imapi2-typelib.ps1`,
  not typed from documentation.
- Design: [LLD-windows-platform.md](../design/LLD-windows-platform.md).
