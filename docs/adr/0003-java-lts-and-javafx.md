<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# 0003 — Java 25 LTS, OpenJFX and Maven

- Status: Accepted
- Date: 2026-10-31
- Deciders: project owner

## Context and problem statement

RetroBurn is a cross-platform desktop app with a themeable UI and offline
rendering (DVD menus, burn reels, PDF covers). Which language level, UI toolkit
and build tool?

## Decision drivers

- One code base and UI for Windows and Linux, macOS later.
- Easy for a typical Java developer to pick up.
- Long support window; no preview features.
- Native installers without a separately installed runtime.
- Skins via stylesheets.

## Considered options

1. Java 25 (current LTS) + OpenJFX 25 + Maven
2. Java 21 (previous LTS) + OpenJFX 21 + Maven
3. Java + Swing
4. Gradle instead of Maven

## Decision outcome

Chosen option: "Java 25 + OpenJFX 25 + Maven with the wrapper committed",
because Java 25 is the latest LTS at project start (support well beyond the
v0.1 timeframe), OpenJFX supports CSS skins and animation out of the box, and
Maven is the most widely known Java build tool with a declarative, easy-to-read
build.

- No preview features.
- `jlink` + `jpackage` produce the per-OS runtime and installers.
- Java2D is used for headless rendering (menus, reels, PNG export) so those
  parts are testable without a display.
- Whether the app itself is a JPMS module (which affects how `jlink` handles
  non-modular jars) is decided in the skeleton milestone with its own ADR.

### Consequences

- Good: long support window; modern language features (records, sealed types,
  pattern matching) make the models clear.
- Good: Swing avoided; JavaFX CSS maps directly onto the theme concept.
- Bad: JavaFX packaging has more moving parts than a plain jar; covered by the
  release engineering docs.
- Bad: contributors need JDK 25 installed (the developer guide explains how).

## More information

- OpenJDK: https://openjdk.org/ — OpenJFX: https://openjfx.io/
- Exact JDK and OpenJFX patch versions are pinned in the build when the
  skeleton is created.
