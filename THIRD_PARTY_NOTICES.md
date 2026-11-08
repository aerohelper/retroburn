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
| Apache PDFBox and FontBox | Writes PDF files of covers and labels and reads fonts | The Apache Software Foundation; original PDFBox and FontBox projects; includes Adobe font metrics, CMaps and glyph lists, Unicode data, the Liberation Sans font, a CC0 ICC profile and parts of TwelveMonkeys ImageIO | Apache-2.0 AND BSD-3-Clause AND APAFML AND Adobe-Glyph AND OFL-1.1 AND CC0-1.0 AND Unicode-TOU | https://pdfbox.apache.org/ | Linux, Windows |
| Apache Commons Logging | Logging used by PDFBox | The Apache Software Foundation | Apache-2.0 | https://commons.apache.org/proper/commons-logging/ | Linux, Windows |
| Lato font (Regular, Bold) | Cover designer text | Łukasz Dziedzic | OFL-1.1 | https://github.com/google/fonts/tree/main/ofl/lato | Linux, Windows |
| VT323 font | Cover designer and the y2k skin | The VT323 Project Authors | OFL-1.1 | https://github.com/google/fonts/tree/main/ofl/vt323 | Linux, Windows |

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

**Apache PDFBox, PDFBox io and FontBox**

> Apache PDFBox
> Copyright 2002-2026 The Apache Software Foundation
>
> This product includes software developed at
> The Apache Software Foundation (http://www.apache.org/).
> Based on source code contributed to the original PDFBox project.
> Copyright (c) 2002-2007, www.pdfbox.org
>
> Includes the Adobe Glyph List
> Copyright 1997, 1998, 2002, 2007, 2010 Adobe Systems Incorporated.
>
> Includes the Zapf Dingbats Glyph List
> Copyright 2002, 2010 Adobe Systems Incorporated.
>
> Includes the Bidi Mirroring Glyph Property (BidiMirroring-8.0.0.txt)
> Copyright 1991-2015 Unicode, Inc.
>
> Includes parts of TwelveMonkeys ImageIO
> Copyright 2008-2016 Harald Kuhr
>
> Includes the CMYK ICC-profile (CGATS001Compat-v2-micro.icc)
> https://github.com/saucecontrol/Compact-ICC-Profiles
> Released to the public domain under the Creative Commons CC0 license by Clinton Ingram, saucecontrol

> Apache FontBox
> Copyright 2008-2026 The Apache Software Foundation
>
> This product includes software developed at
> The Apache Software Foundation (http://www.apache.org/).
> Based on source code contributed to the original FontBox project.
> Copyright (c) 2006-2007, www.fontbox.org
>
> Includes the Script Property (Scripts-10.0.0.txt)
> Copyright 2017 Unicode, Inc.

PDFBox io carries the standard Apache notice ("Apache PDFBox io, Copyright
2002-2026 The Apache Software Foundation").

**Apache Commons Logging**

> Apache Commons Logging
> Copyright 2001-2026 The Apache Software Foundation
>
> This product includes software developed at
> The Apache Software Foundation (https://www.apache.org/).

The verbatim NOTICE files are in `src/main/resources/credits/notices/`. The
full PDFBox and FontBox licence files, which list the terms of every
external component they contain, are in `src/main/resources/credits/licenses/`.

## Used from the operating system (not distributed)

| Component | Used for | Authors | Licence (SPDX) | Homepage | Platforms |
|---|---|---|---|---|---|
| GNU xorriso | Burns data discs and images, erases and reads discs | Thomas Schmitt and the libburnia project | GPL-3.0-or-later | https://www.gnu.org/software/xorriso/ | Linux |
| cdrskin | Burns audio CDs with CD-Text | Thomas Schmitt and the libburnia project | GPL-2.0-or-later | https://dev.lovelyhq.com/libburnia/web/wiki/Cdrskin | Linux |
| genisoimage (cdrkit) | Builds DVD-Video disc images | The cdrkit project, based on mkisofs by Jörg Schilling | GPL-2.0-only | https://salsa.debian.org/debian/cdrkit | Linux |
| FFmpeg | Converts audio and video, thumbnails, burn reels | The FFmpeg developers | GPL-2.0-or-later (as built by the distribution) | https://ffmpeg.org/ | Linux |
| dvdauthor and spumux | Video DVD authoring and menu buttons | Scott Smith, Lawrence D'Oliveiro and contributors | GPL-2.0-or-later | https://github.com/ldo/dvdauthor | Linux |
| Windows Image Mastering API (IMAPI2) | Drives, burning, erasing, disc images | Microsoft Corporation | Part of Windows | https://learn.microsoft.com/windows/win32/imapi/disc-mastering-api | Windows |

The Linux package declares these as dependencies; they come from your
distribution with their own licences and sources.

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
corresponding source published); cdrdao (Windows, if adopted).
