<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# 0005 — Reach IMAPI2 through a PowerShell helper

- Status: Proposed — to be confirmed or replaced by the Windows engine spike (M3)
- Date: 2026-10-31
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

1. **PowerShell helper** — a bundled, readable PowerShell 5.1 script (ships with
   Windows 10/11) that uses the IMAPI2 COM objects and prints events as JSON
   Lines on stdout.
2. **JNA COM bindings** in-process.
3. A small compiled helper (C#/C++) — rejected up front: adds a second
   language toolchain and a binary to build, sign and audit.

## Decision outcome

Proposed: **option 1, PowerShell helper**, subject to a spike that must show:

- progress events from the IMAPI2 data writer reach the script reliably
  (COM event sinks from PowerShell are the main uncertainty);
- an IMAPI2FS image can be written to a file without a drive present;
- the device add/remove notification works, or polling is adequate;
- `SetThreadExecutionState` can be held by the helper for the burn's duration;
- the script runs as a standard user under default execution policies when
  started with an explicit argument list, without weakening system policy;
- antivirus products do not block it in common configurations.

If the spike fails any of these in a way that cannot be worked around, switch
to **option 2 (JNA)**, register JNA (Apache-2.0) in the third-party notices,
and record the outcome by superseding this ADR.

Protocol (if option 1 is confirmed):

- Java starts PowerShell with an argument list naming the bundled script.
- The request (operation, drive id, paths, options) is sent as **one JSON
  document on stdin**. No values are ever interpolated into script text.
- The helper writes one JSON object per line to stdout:
  `progress`, `log`, `drive`, `result`, `error`.
- A pure Java parser maps these to `BurnEvent`s and typed exceptions; it is
  tested with captured real output.

### Consequences

- Good: mirrors the Linux model; helper crash cannot crash the app; if the
  helper dies, its execution-state request dies with it.
- Good: the script is plain text a maintainer can read and debug.
- Bad: PowerShell start-up adds about a second per operation (acceptable for
  burns; drive polling should reuse a long-lived helper or poll sparingly).
- Bad: some security products scrutinise PowerShell; tracked in RISKS.md.

## More information

- IMAPI2 reference: Microsoft Learn, "Image Mastering API" documentation.
- The spike's findings (what was verified, on which Windows build and drive)
  will be recorded here or in the superseding ADR.
