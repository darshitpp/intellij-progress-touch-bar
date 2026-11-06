# Contributing

Bug reports and pull requests are welcome.

## Reporting a bug

Include your Mac model, macOS version, IDE name and version, any progress bar theme plugin you use, and the relevant lines from `idea.log` (**Help | Show Log in Finder**, search for "Progress Touch Bar").

## Building

Requires JDK 25. The Gradle wrapper downloads everything else, including the target IDE.

| Command | What it does |
|---|---|
| `./gradlew buildPlugin` | Builds the plugin zip into `build/distributions/` |
| `./gradlew runIde` | Starts a sandbox IDE with the plugin installed |
| `./gradlew test` | Runs the unit tests |
| `./gradlew verifyPlugin` | Checks compatibility, as the Marketplace does |

To test a build in your own IDE, bump `version` in `build.gradle.kts`: the IDE silently skips installing the same version again, and a reinstall only takes effect after a restart.

## How it's built

- `core/`: IDE-free logic, fully unit-tested: when to show the bar, rendering frames (theme or standard bar), failure handling.
- `ide/`: thin wiring into the IDE: polling running tasks, the frame loop, settings.
- `mac/`: the native Touch Bar, called directly through Java's Foreign Function & Memory API.

The IDE's own Touch Bar API is internal, so the plugin builds its own `NSTouchBar` and swaps it in while a task runs. Touch Bar behaviour can only be checked on a physical Touch Bar Mac; everything else is covered by tests. `screencapture -b touchbar.png` takes a screenshot of the Touch Bar.

One rule that bites: never keep a native pointer across calls. Resolve the window and view each time.

## Pull requests

- Write a failing test first for any logic change in `core/`.
- Run `./gradlew test verifyPlugin` before opening the PR.
- For Touch Bar changes, mention which Mac you tested on.
