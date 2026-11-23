<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# 0008 — Hand-wired composition root, no DI framework

- Status: Accepted
- Date: 2026-10-31
- Deciders: project owner

## Context and problem statement

Objects such as engines, the pipeline runner, the event bus and settings must
be created and connected somewhere. How?

## Decision drivers

- A new developer should be able to find where any object comes from by
  reading code, not by understanding framework rules.
- No reflection or annotation magic; fast start-up (target under 3 s).
- Easy substitution of the simulated platform in tests and demos.

## Considered options

1. Constructor injection, wired by hand in one `AppContext` class
2. A DI framework (Guice, Dagger, Spring)
3. Service locator / static singletons

## Decision outcome

Chosen option: "hand wiring in `AppContext`". Every dependency is passed
through a constructor. `AppContext` chooses the `PlatformServices`
implementation (Linux, Windows or simulated) at start-up and is the only class
that does so. Tests build the pieces they need directly.

### Consequences

- Good: "go to definition" answers every wiring question; no extra dependency.
- Good: no hidden global state.
- Bad: `AppContext` grows as features are added; if it passes ~300 lines it is
  split into small per-feature factory classes, still wired by hand.
