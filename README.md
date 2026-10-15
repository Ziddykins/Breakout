# Breakout

A clean, lightweight Breakout-style game implemented in Kotlin for Android.

![screenshot](docs/screenshot.png)

## Highlights

- Classic Breakout gameplay: bounce the ball, break bricks, score points.
- Built with Kotlin and Android tooling.
- Small, easy-to-read codebase — great for learning game loops and simple physics on Android.

## Features

- Multiple levels and increasing difficulty
- Power-ups (expand paddle, multi-ball, slow ball) — if present, list them here
- Smooth touch controls and configurable input
- High score tracking (local)

> Tip: Add a `docs/screenshot.png` or a GIF at `docs/demo.gif` to show the game in action.

## Getting started

Prerequisites

- Android Studio (2020.3 / Arctic Fox or newer recommended)
- Android SDK (API level matching the project configuration)
- JDK 11+

Build and run

1. Clone the repository

   git clone https://github.com/Ziddykins/Breakout.git
   cd Breakout

2. Open the project in Android Studio
3. Let Gradle sync and install any required SDK components
4. Run on an emulator or a physical device

If you prefer the command line, use the Gradle wrapper:

   ./gradlew assembleDebug
   ./gradlew installDebug

## Controls

- Touch the screen (or drag) to move the paddle.
- Keyboard/gamepad controls work in the emulator if configured.

Adjustments and input options may be available in the app settings.

## Project Layout

- app/ — Android application module
- src/ — Kotlin source files (game logic, rendering, input)
- assets/ or res/ — graphics and level data

(Adjust the layout above to match the actual repository structure.)

## Contributing

Contributions are welcome! Suggested workflow:

1. Fork the repo
2. Create a feature branch (git checkout -b feat/awesome-stuff)
3. Make changes and add tests where appropriate
4. Open a Pull Request describing your changes

Please follow Kotlin style conventions and write clear commit messages.

## Issues

Open an issue for bugs, feature requests, or questions. Provide device/emulator details and steps to reproduce.

## License

This repository does not include an explicit license file yet. If you want others to use and contribute to this project, consider adding a LICENSE (for example, the MIT license).

## Acknowledgements

- Inspired by the classic Breakout and other simple mobile arcade games.


---

If you'd like, I can:
- Add badges (build/test/license) if you have CI set up,
- Add a demo GIF into docs/ and reference it,
- Tailor the README to the exact project structure and features if you point me to key files (e.g., where levels, controls, and save data live).
