# Contributing to Hyper Bridge

Thank you for your interest in contributing!

## Getting Started
1.  Clone the repo.
2.  Open in Android Studio (Ladybug or newer recommended).
3.  Sync Gradle and run on a device/emulator (API 26+).

## Code Style
* We use **Kotlin** exclusively.
* UI must be written in **Jetpack Compose** (Material 3).
* Follow the existing package structure:
    * `ui/`: Screens and Components.
    * `service/`: Background logic and Translators.
    * `data/`: Preferences and Models.

## Pull Requests
* **Do not push directly to `main`.**
* Create a branch for your feature/fix.
* Provide a clear description of what you changed.
* If you are adding a UI feature, **attach a screenshot** to the PR.

## Reporting Bugs
Please use the Issues tab and provide:
* Device Model & MIUI/HyperOS Version.
* Steps to reproduce.
* Crash logs (if applicable).
