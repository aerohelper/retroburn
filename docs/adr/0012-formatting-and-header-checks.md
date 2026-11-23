<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# 0012 — Automated formatting, licence headers and coverage in the build

- Status: Accepted
- Date: 2026-10-31
- Deciders: project owner

## Context and problem statement

Code must look the same everywhere, every source file must carry an SPDX
header, and non-UI code needs at least 70 % unit-test coverage. These checks
must run identically on Linux and Windows and locally.

## Considered options

1. Spotless (Palantir Java Format + licence header step) and JaCoCo in the
   Maven build; `reuse lint` in CI
2. Checkstyle rules only
3. Editor settings only

## Decision outcome

Chosen option: "1".

- **Spotless** with **Palantir Java Format** (4-space indentation, 120-column
  lines — closer to common Java style than Google Java Format's 2 spaces).
  `spotless:check` runs in `verify`; `mvnw spotless:apply` fixes formatting.
- Spotless's licence-header step requires the two-line SPDX header on every
  Java file.
- **JaCoCo** enforces ≥ 70 % line coverage on everything except `app`, `ui`
  and `theme` (JavaFX views), which are covered by manual tests.
- **`reuse lint`** (FSFE REUSE tool) runs in CI and checks licence information
  for every file, including non-Java files.

All three are build/test-time only and listed in the build section of
`THIRD_PARTY_NOTICES.md`.

### Consequences

- Good: no formatting debates in review; compliance checked automatically.
- Bad: contributors must run `spotless:apply` before committing (the developer
  guide says so; CI points it out otherwise).
