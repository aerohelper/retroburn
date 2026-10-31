<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# 0009 — Platform isolation enforced by ArchUnit

- Status: Accepted
- Date: 2026-10-31
- Deciders: project owner

## Context and problem statement

RetroBurn runs on Linux and Windows now and should gain macOS later without
touching the UI, project, pipeline, audio, video, design or reel code. OS
checks scattered through the code would make that impossible. How do we keep
OS-specific code contained?

## Considered options

1. Convention and code review only
2. Convention plus automated architecture tests (ArchUnit, test scope only)
3. Separate Maven modules per platform

## Decision outcome

Chosen option: "ArchUnit tests", because they fail the build on violations
without the overhead of a multi-module build.

Rules enforced:

- Only `platform.<os>` packages contain OS-specific code; no OS-name checks
  elsewhere.
- Layer dependencies point downwards only (see HLD §1).
- `platform.*` implementations do not depend on each other.
- Nothing outside `app` depends on `ui`; JavaFX is used only in `app`, `ui`,
  `theme` and the design editor.
- Only `app.AppContext` instantiates a concrete `PlatformServices`.

ArchUnit is Apache-2.0 and used only at test time; it is listed in the
build/test section of the third-party notices.

### Consequences

- Good: adding an OS means adding a package and one line in `AppContext`.
- Bad: rules must be updated deliberately when the layering changes — which is
  the point.
