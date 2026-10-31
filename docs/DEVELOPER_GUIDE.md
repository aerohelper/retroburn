<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Developer guide

Goal: from a fresh machine to RetroBurn running from source in under 30
minutes, on Linux or Windows.

## 1. Install the prerequisites

You need **JDK 25** and **git**. Maven is downloaded by the wrapper (`mvnw`),
and JavaFX comes from Maven Central, so neither needs a separate install.

### Linux (Ubuntu / Debian)

```sh
sudo apt install git unzip   # the Maven wrapper needs unzip to verify its download
# JDK 25: use your distribution's openjdk-25-jdk package if available,
# otherwise install Eclipse Temurin 25 from https://adoptium.net/
java -version   # must report version 25
```

Burning features (from the core milestone on) also need the external tools:

```sh
sudo apt install xorriso cdrskin ffmpeg dvdauthor genisoimage
```

To use an optical drive without root, your user must be in the `cdrom` group:
`sudo usermod -aG cdrom "$USER"`, then log out and back in.

### Windows 10/11

1. Install **Eclipse Temurin 25 (JDK)** from https://adoptium.net/ and tick
   "Set JAVA_HOME" in the installer.
2. Install **Git for Windows** from https://git-scm.com/.
3. Open a new terminal and check `java -version` reports 25.

The Windows burning tools are bundled with the installer; for development they
will be fetched by a script added with the Windows engine.

## 2. Get the code

```sh
git clone https://github.com/aerohelper/retroburn.git
cd retroburn
git config core.hooksPath tools/git-hooks   # installs the pre-commit safety checks
```

## 3. Build, test, run

| Task | Linux | Windows |
|---|---|---|
| Full build with all checks | `./mvnw verify` | `mvnw.cmd verify` |
| Run the app | `./mvnw javafx:run` | `mvnw.cmd javafx:run` |
| Fix formatting | `./mvnw spotless:apply` | `mvnw.cmd spotless:apply` |
| Run one test class | `./mvnw test -Dtest=ComponentRegistryTest` | `mvnw.cmd test -Dtest=ComponentRegistryTest` |

`verify` compiles with all warnings on, runs unit and architecture tests,
checks formatting and SPDX headers, checks that every runtime dependency is
credited, and enforces 70 % line coverage outside the JavaFX view packages.
The coverage report is in `target/site/jacoco/index.html`.

The first run downloads Maven and the dependencies (a few minutes); later runs
are quick.

### Licence compliance check (optional locally, always in CI)

```sh
pipx install "reuse[charset-normalizer]"
reuse lint
```

## 4. IDE set-up

Any IDE with Maven support works (IntelliJ IDEA, Eclipse, VS Code with the
Java extensions): open the folder as a Maven project and select JDK 25. To run
from the IDE, use the main class `io.aerohelper.retroburn.app.Main`; if the IDE
puts JavaFX on the classpath it still starts (see ADR-0010), but
`mvnw javafx:run` is the reference way.

## 5. Find your way around

- [architecture/HLD.md](architecture/HLD.md) — layers and modules.
- `docs/design/LLD-*.md` — one per module, each with a "How to change this
  safely" section.
- [adr/](adr/) — why things are the way they are.
- [CONTRIBUTING.md](../CONTRIBUTING.md) — code, commit and review rules.

Notes on libraries:

- **Jackson 3** packages are `tools.jackson.*`, except the annotations, which
  stay `com.fasterxml.jackson.annotation`. Many online examples show Jackson
  2's `com.fasterxml.jackson.databind`; translate accordingly.
- **JavaFX** classes may only be used in `app`, `ui`, `theme` and `design` —
  the architecture tests fail otherwise.

## 6. Troubleshooting

| Symptom | Fix |
|---|---|
| "RetroBurn needs JDK 25 or newer" | `JAVA_HOME` points at an older JDK. |
| `spotless:check` fails | Run `mvnw spotless:apply` and commit the result. |
| `CreditsCompletenessTest` fails | You added a runtime dependency without registering it; see [design/LLD-credits.md](design/LLD-credits.md). |
| `ArchitectureTest` fails | A dependency breaks the layering; see the rule's message and the HLD. |
| Commit refused by the pre-commit hook | You staged a private planning file; unstage it. |
