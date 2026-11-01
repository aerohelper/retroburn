---
name: run-retroburn
description: Launch the RetroBurn JavaFX desktop app on this machine and drive it - press home-screen cards and buttons, take screenshots of the window, read the visible labels. Use to run, start or screenshot RetroBurn, or to check a UI change in the real app rather than the tests.
---

# Run and drive RetroBurn

RetroBurn is a JavaFX desktop app. To just open it for a person, run
`./mvnw javafx:run`. To drive it yourself and look at the result, use the
harness in this folder: `ScreenTour.java` starts the real app (same
`AppContext` and `MainWindow` as a normal start, on this machine's real
platform and drives), runs a list of steps and closes it.

## Safety

The app talks to real drives. **Never** press `Burn disc`, `Erase disc…`,
`Create ISO` in a confirm dialog, or anything else that writes, unless the
owner has said in this conversation that the disc in the drive may be
written. Opening screens, `Home`, `Refresh` and snapshots are safe.

## 1. Build the app and get its classpath

From the repo root (Git Bash on Windows; on Linux use `:` in place of `;`
in classpaths below):

```bash
./mvnw -q -o compile
./mvnw -q -o dependency:build-classpath -Dmdep.outputFile="$SCRATCH/cp.txt"
```

Drop `-o` if the local Maven repository is empty. `$SCRATCH` is any
scratch folder outside the repo.

## 2. Compile the harness

```bash
CP="target/classes;$(cat "$SCRATCH/cp.txt")"
mkdir -p "$SCRATCH/tour/classes" "$SCRATCH/tour/out"
javac -d "$SCRATCH/tour/classes" -cp "$CP" .claude/skills/run-retroburn/ScreenTour.java
```

## 3. Run steps

Launch the nested `ScreenTour$Main` class, not `ScreenTour` itself: the java
launcher refuses an `Application` main class on the classpath with "JavaFX
runtime components are missing".

```bash
timeout 120 java -cp "$SCRATCH/tour/classes;$CP" 'ScreenTour$Main' "$SCRATCH/tour/out" \
  snap:1-home \
  card:"Data disc" wait:9000 snap:2-data-disc button:Home wait:1000 \
  card:"Burn ISO" wait:9000 snap:3-burn-iso button:Home wait:500 \
  card:"Create ISO" wait:1500 snap:4-create-iso \
  2>&1 | grep -v '^WARNING'
```

Steps: `card:<title>` presses the home card whose title is `<title>`;
`button:<text>` presses a button by its visible text; `wait:<ms>` sleeps;
`snap:<name>` writes `<out>/<name>.png` and prints every visible label.
Each press also prints whether the control was disabled.

Waits: a screen with a drive bar reads the drives and the media in the
background, which takes several seconds with a real USB writer; 9 s is
enough. Screens without a drive bar need about 1 s.

## 4. Look at the result

Open every PNG with the Read tool and check it; a blank or grey frame means
the app failed to start (read the console output above it). The printed
labels give the exact texts for the report.

## Notes

- On an OS other than Windows or Linux the app uses the simulated platform,
  with a fake drive and settings under `~/.retroburn-simulated`.
- The harness uses the real user settings and work folder of this machine.
- Headless Linux needs a display: run under `xvfb-run -a`.
