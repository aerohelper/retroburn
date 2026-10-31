<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Third-party notices

RetroBurn is licensed under GPL-3.0-or-later. It includes or uses the
components below. The same list, with every full licence and notice text, is
shown in the app under **Help → Open-source licenses** and lives in
`src/main/resources/credits/`. This file and `components.json` are kept in
sync; a build test fails if a runtime dependency is missing from the register.

## Included in RetroBurn packages

| Component | Used for | Authors | Licence (SPDX) | Homepage | Platforms |
|---|---|---|---|---|---|
| OpenJDK | Java runtime packaged with RetroBurn | Oracle and/or its affiliates and the OpenJDK community | GPL-2.0-only WITH Classpath-exception-2.0 | https://openjdk.org/ | Linux, Windows |
| OpenJFX | User interface toolkit | Oracle and/or its affiliates and the OpenJFX community | GPL-2.0-only WITH Classpath-exception-2.0 | https://openjfx.io/ | Linux, Windows |
| Jackson databind and annotations | Reads and writes JSON files | Tatu Saloranta and the Jackson community (FasterXML) | Apache-2.0 | https://github.com/FasterXML/jackson | Linux, Windows |
| Jackson core (with FastDoubleParser and Schubfach) | JSON parsing and number formatting used by Jackson | Tatu Saloranta and the Jackson community; FastDoubleParser by Werner Randelshofer; Schubfach by Raffaello Giulietti | Apache-2.0 AND MIT AND BSL-1.0 AND BSD-2-Clause | https://github.com/FasterXML/jackson-core | Linux, Windows |

The OpenJDK runtime image also carries the third-party notices of the JDK
modules it contains, in its `legal/` folder.

### Apache-2.0 NOTICE contents

**Jackson (databind, annotations, core)**

> Jackson is a high-performance, Free/Open Source JSON processing library.
> It was originally written by Tatu Saloranta (tatu.saloranta@iki.fi), and has
> been in development since 2007. It is currently developed by a community of
> developers.
>
> Copyright 2007-, Tatu Saloranta (tatu.saloranta@iki.fi)
>
> jackson-core bundles a shaded copy of FastDoubleParser
> <https://github.com/wrandelshofer/FastDoubleParser>, available under an MIT
> license. Copyright © 2023 Werner Randelshofer, Switzerland. See
> FastDoubleParser-LICENSE and FastDoubleParser-ThirdParty-LICENSE for other
> source code included in FastDoubleParser.
>
> jackson-core bundles a copy of the Schubfach number writing code
> <https://github.com/c4f7fcce9cb06515/Schubfach>, available under an MIT
> license. Copyright 2018-2020 Raffaello Giulietti.

The verbatim NOTICE files are in `src/main/resources/credits/notices/`.

## Build and test tools (not distributed)

| Component | Used for | Licence (SPDX) | Homepage |
|---|---|---|---|
| Apache Maven and the Maven Wrapper | Build | Apache-2.0 | https://maven.apache.org/ |
| Maven plugins (compiler, resources, surefire, jar, dependency, enforcer, clean, install) | Build | Apache-2.0 | https://maven.apache.org/plugins/ |
| JUnit 6 | Unit tests | EPL-2.0 | https://junit.org/ |
| ArchUnit | Architecture tests | Apache-2.0 | https://www.archunit.org/ |
| JaCoCo | Test coverage | EPL-2.0 | https://www.jacoco.org/ |
| Spotless | Formatting and licence-header checks | Apache-2.0 | https://github.com/diffplug/spotless |
| Palantir Java Format | Code formatter | Apache-2.0 | https://github.com/palantir/palantir-java-format |
| javafx-maven-plugin | Running the app during development | Apache-2.0 | https://github.com/openjfx/javafx-maven-plugin |
| REUSE tool | Licence compliance check in CI | GPL-3.0-or-later (tool) | https://reuse.software/ |

## Planned components

These will be registered here, in the in-app credits and with their licence
texts when the features using them are built: GNU xorriso, cdrskin,
genisoimage (Linux); the Windows Image Mastering API (Windows, part of the OS);
FFmpeg, dvdauthor and spumux (both; bundled on Windows with their
corresponding source published); cdrdao (Windows, if adopted); Apache PDFBox
with FontBox and Commons Logging; the VT323 font.
