<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Security policy

## Supported versions

RetroBurn has not had a release yet. Once it does, security fixes go into the
latest release.

## Reporting a vulnerability

Please **do not open a public issue** for security problems.

Report privately through GitHub: open the repository's **Security** tab and
choose **Report a vulnerability**. Include what you found, how to reproduce it,
and which OS and RetroBurn version you used.

We aim to acknowledge reports within a week and to agree on a disclosure date
with you.

## Scope

RetroBurn is a local desktop app with no network access. Relevant issues
include, for example:

- crafted media, image or project files that make RetroBurn misbehave;
- user data reaching a command line, script or generated file unescaped;
- path traversal when opening project or design files;
- problems with the external programs bundled in the Windows installer.

The design-level analysis is in
[docs/SECURITY_THREAT_MODEL.md](docs/SECURITY_THREAT_MODEL.md).
