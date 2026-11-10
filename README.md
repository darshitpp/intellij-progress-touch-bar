# Progress Touch Bar

**Your progress bar theme, now on your macOS Touch Bar.**

[![JetBrains Marketplace](https://img.shields.io/jetbrains/plugin/v/35859-progress-touch-bar?label=Marketplace)](https://plugins.jetbrains.com/plugin/35859-progress-touch-bar)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/35859-progress-touch-bar)](https://plugins.jetbrains.com/plugin/35859-progress-touch-bar)

![Progress Touch Bar filling up while indexing, with percentage and a Hide button](screenshots/touchbar.gif)

This IntelliJ plugin puts the IDE's background work on your Mac's Touch Bar: indexing, Gradle syncs, builds, anything that shows progress in the status bar. Never look for the tiny bar in the corner of the IDE again!

- Animated, full-height bar with the task name and optional percentage.
- Uses your progress bar theme, or the IDE's standard bar. Popular themes:
  [Nyan Progress Bar](https://plugins.jetbrains.com/plugin/8575-nyan-progress-bar),
  [Mario Progress Bar](https://plugins.jetbrains.com/plugin/14708-mario-progress-bar),
  [Pokemon Trainer Progress Bar](https://plugins.jetbrains.com/plugin/14609-pokemon-trainer-progress-bar).
- Appears only for tasks that run longer than half a second; disappears when they end.
- Tap **Hide** to get your normal Touch Bar back.

## Install

In the IDE: **Settings | Plugins | Marketplace**, search for "Progress Touch Bar". Or get it from the [Marketplace page](https://plugins.jetbrains.com/plugin/35859-progress-touch-bar), or download the zip from [Releases](https://github.com/darshitpp/intellij-progress-touch-bar/releases) and use **Install Plugin from Disk...**.

## Requirements

- A Mac with a Touch Bar, IntelliJ IDEA 2026.1+ (or another JetBrains IDE on the same platform).
- Touch Bar support on in the IDE (off by default since 2025.1): **Help | Find Action | Registry**, enable `ide.mac.touchbar.enabled`, restart.
- The IDE not set to always show function keys (**System Settings | Keyboard | Keyboard Shortcuts | Function Keys**).

## Settings

**Settings | Tools | Progress Touch Bar**

| Setting | Default | Range |
|---|---|---|
| Show percentage next to the progress bar | Off | |
| Show after (ms): how long a task runs before the bar appears | 500 | 0–10,000 |
| Frame rate (fps): higher is smoother but uses more CPU | 30 | 10–60 |

## Troubleshooting

If something fails, the plugin switches itself off until the IDE restarts and gives you back the IDE's normal Touch Bar. It then writes the reason to the IDE log:

1. **Help | Show Log in Finder**, then open `idea.log`.
2. Search for `Progress Touch Bar`. The line "disabled for this session" has the cause and a stack trace; "started" or "not supported here" show whether the plugin is active.

When reporting a problem, include those lines.

[Contributing](CONTRIBUTING.md) · [Apache License 2.0](LICENSE)
