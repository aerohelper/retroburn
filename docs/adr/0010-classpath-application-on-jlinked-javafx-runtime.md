<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# 0010 — Classpath application on a jlinked JavaFX runtime

- Status: Accepted
- Date: 2026-10-31
- Deciders: project owner

## Context and problem statement

`jlink` can only put explicit Java modules into a runtime image. RetroBurn's
own code could be a JPMS module, but some planned libraries (notably Apache
PDFBox) ship only as automatic modules, which `jlink` rejects. How should the
application be structured so packaging stays simple?

## Considered options

1. RetroBurn and all libraries as explicit modules, jlinking everything
   (requires patching or repackaging non-modular libraries)
2. RetroBurn on the classpath; `jlink` builds a runtime containing the needed
   JDK modules plus the JavaFX modules; `jpackage` adds the application jars
3. Fat jar with JavaFX on the classpath

## Decision outcome

Chosen option: "2". The app has no `module-info.java`. JavaFX lives on the
module path (inside the runtime image in packages, added by
`javafx-maven-plugin` during development), which is the configuration JavaFX
supports. A small `Main` class that does not extend `Application` is the entry
point so the app also starts in tools that put everything on the classpath.

### Consequences

- Good: any jar can be used without repackaging; tests need no module patching.
- Good: JavaFX runs in its supported configuration.
- Bad: no compile-time module encapsulation; layering is enforced by ArchUnit
  instead (ADR-0009).
- Option 3 rejected: JavaFX on the classpath is an unsupported configuration
  and logs a warning at start-up.
