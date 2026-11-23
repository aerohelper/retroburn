<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# 0004 — Use external tools as separate processes

- Status: Accepted
- Date: 2026-10-31
- Deciders: project owner

## Context and problem statement

Burning, imaging, encoding and DVD authoring are hard problems already solved
by mature tools (GNU xorriso, cdrskin, genisoimage, FFmpeg, dvdauthor/spumux,
possibly cdrdao). How should RetroBurn use them?

## Decision drivers

- Reliability: reuse proven tools instead of reimplementing them.
- Licence clarity: avoid linking GPL/LGPL native code into the JVM.
- Crash isolation: a tool crash must not take down the app.
- Testability: tool output must be capturable and replayable in tests.
- Security: no shell interpretation of user-controlled data.

## Considered options

1. Run each tool as a child process with an argument list
2. Link native libraries (libburn, libav*) through JNI/JNA
3. Reimplement in pure Java

## Decision outcome

Chosen option: "child processes", with these rules:

- Processes are started with `ProcessBuilder` and an **argument list** — never
  a shell command string.
- Each tool's command line is built in exactly one `…CommandBuilder` class,
  with a comment per argument explaining why it is there and linking the
  tool's documentation.
- Options and output formats are verified against real documentation and
  captured output before code depends on them; captured output becomes test
  fixtures under `src/test/resources/tool-output/<tool>/`.
- Output is parsed by pure parser classes, unit-tested against those fixtures.
- stdout and stderr are always drained on background threads; every wait has a
  timeout; cancellation destroys the process tree.
- Full command lines and output are logged.

### Consequences

- Good: robust, licence-clean, isolated, testable.
- Good: same model on Linux and Windows (the Windows IMAPI2 bridge is also
  proposed as a process — see ADR-0005).
- Bad: progress depends on what each tool prints; parsers can break when tool
  versions change, so tool versions are detected and logged.
- Bad: process start-up cost (negligible against burn and encode times).
