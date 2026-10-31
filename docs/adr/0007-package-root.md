<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# 0007 — Java package root

- Status: Accepted
- Date: 2026-10-31
- Deciders: project owner

## Context and problem statement

Java packages need a stable, unique root. The project is hosted on GitHub under
the account `aerohelper`.

## Considered options

1. `io.aerohelper.retroburn`
2. `io.github.aerohelper.retroburn` — the GitHub-based convention used for
   Maven Central publishing
3. A purchased domain (e.g. `org.retroburn`)

## Decision outcome

Chosen option: "`io.aerohelper.retroburn`", the owner's choice: short and
readable, and tied to the project's `aerohelper` identity. The Maven `groupId`
is `io.aerohelper` and the `artifactId` is `retroburn`.

### Consequences

- Good: short package names throughout the code base.
- Bad: if RetroBurn's libraries were ever published to Maven Central, Central
  would ask for proof of control of the matching domain (`aerohelper.io`) or a
  switch to `io.github.aerohelper`. RetroBurn ships as an application, not a
  library, so this does not apply today.
- If the app is renamed before the first release, the last segment changes.
