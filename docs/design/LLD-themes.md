<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# LLD — Themes, sounds and the spinning disc (`theme`, screens)

| | |
|---|---|
| Status | Designed for the retro skins milestone (M9) |
| Packages | `io.aerohelper.retroburn.theme` (themes, sounds, disc animation; JavaFX), `.app` (View menu), `.ui` (progress and result screens) |
| Related | [ADR-0017](../adr/0017-sounds-and-themes.md), [UX_SPEC §7–9](../ux/UX_SPEC.md), [EXTENSIBILITY §4](../EXTENSIBILITY.md) |

## 1. Scope

- Two themes, `classic` (default) and `y2k`, switched live from
  **View → Theme** and remembered.
- `y2k`: bevelled buttons, a gradient header, a segmented LED-style
  progress bar and the VT323 display font for headings and the percentage.
- A spinning disc on the progress and result screens whose speed follows
  the burn: still when idle, spinning up, spinning while burning, slowing
  to a stop when done.
- Sounds for spin-up, a quiet burning loop, success and error; on by
  default only in `y2k`; **View → Sounds** turns them on or off.
- **View → Animate the disc** stops the animation; it also stays still
  when the system asks for reduced motion
  (`Platform.getPreferences().isReducedMotion()`, JavaFX 25).

All artwork and sounds are original: CSS colours and shapes, and sounds
made by a generator in `tools/sounds/` (ADR-0017). No logos, icons, sounds
or names of other products.

## 2. Theme folders (`theme.Theme`, `theme.ThemeRegistry`)

`src/main/resources/themes/<id>/`:

| File | Purpose |
|---|---|
| `theme.properties` | `name`, `author`, `license`, `soundsDefault` (`true`/`false`), `fonts` (comma-separated resource paths loaded before the stylesheet) |
| `theme.css` | JavaFX CSS; `y2k` starts with `@import "../classic/theme.css";` and overrides the tokens and a few components |
| `sounds/spin-up.wav`, `burning-loop.wav`, `success.wav`, `error.wav` | 22 050 Hz, mono, 16-bit PCM |

`ThemeRegistry` reads the built-in ids (`classic`, `y2k`) and returns
`Theme(id, name, soundsDefault, stylesheet, fonts, soundFolder)`.
`apply(Scene, Theme)` loads the theme's fonts with `Font.loadFont` (once)
and replaces the scene's RetroBurn stylesheet, so open screens and dialogs
restyle at once (dialogs copy their owner's stylesheets).

## 3. Tokens

The `classic` values of UX_SPEC §7 are kept. `y2k`:

| Token | `y2k` |
|---|---|
| `-rb-bg` | `#C9D6E8` |
| `-rb-surface` | `#E6EEF8` |
| `-rb-text` | `#0B1A33` |
| `-rb-text-muted` | `#33476B` |
| `-rb-accent` | `#1E5FB8` |
| `-rb-accent-text` | `#FFFFFF` |
| `-rb-success` | `#1A6E30` |
| `-rb-warning` | `#7A4A00` |
| `-rb-error` | `#A61F1F` |
| `-rb-focus` | `#FF9F1C` |
| `-rb-led` | `#39FF14` on `-rb-led-bg` `#0B1A0B` |

Some proposed `y2k` values were darkened so every pair checked for
`classic` also meets WCAG AA in `y2k`; `ThemeContrastTest` checks both
themes, plus the LED colour on its background.

## 4. Sounds (`theme.SoundPlayer`)

- `javax.sound.sampled`: each WAV is opened as a `Clip` on a background
  thread; `play(Sound)` restarts it, `loop(Sound)` loops it, `stopAll()`
  stops everything.
- No audio device, or a format the system refuses: logged once, then
  silent. Sounds never block a burn.
- Enabled when the setting says so, else the theme's `soundsDefault`.
- The burn flow plays spin-up when writing starts, then the loop; on the
  result it stops the loop and plays success (succeeded, with or without
  verify) or error (failed, verify failed, drive lost); nothing on cancel.
- Every sound goes with something visible (UX_SPEC §8).

## 5. The disc (`theme.DiscMotion`, `theme.DiscAnimation`)

`DiscMotion` is plain Java (testable): a state and an angular speed in
degrees per second, updated with the time since the last frame.

| State | Behaviour |
|---|---|
| `IDLE` | 0 °/s |
| `SPIN_UP` | accelerates at 600 °/s² to 720 °/s, then becomes `BURNING` |
| `BURNING` | 720 °/s |
| `DONE` | decelerates at 480 °/s² to 0 |
| `STOPPED` | decelerates at 1 440 °/s² to 0 (error or cancel) |

`DiscAnimation` is a JavaFX `Group` (a disc with a hub, a hole and a few
semi-transparent arcs as sheen, styled by the theme through style classes)
rotated by an `AnimationTimer` that feeds `DiscMotion`. With animation off
it is drawn still.

## 6. Settings (`Settings.Appearance`)

| Key | Default |
|---|---|
| `appearance.theme` | `classic` |
| `appearance.sounds` | empty (the theme's default), `ON` or `OFF` |
| `appearance.animateDisc` | `true` |

## 7. Testing

- Unit: `DiscMotion` speeds and transitions; `ThemeRegistry` reads both
  themes and their sound files; every sound file is 22 050 Hz mono 16-bit
  PCM and within its expected length; `ThemeContrastTest` for both themes;
  `SettingsStore` round trip.
- Manual: switch themes on each screen, including dialogs; burn and
  cancel with sounds on and off; check the disc stays still with
  animation off or reduced motion on.
