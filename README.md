# Breakout

[![License: GPL v3](https://shields.io)](https://gnu.org)


Breakout helps people buying second-hand Android devices stay secure and avoid paying for a device that is still managed, restricted, or quietly targeted. It identifies device-management threats, including devices accidentally or coercively added to corporate profiles, and provides focused removal playbooks through Shizuku. Built for clear diagnostics, informed purchases, and keeping control of your device.

The available playbooks and the actions they perform are shown plainly before you run them. By using Breakout, you acknowledge that you choose to run those actions and accept responsibility for the outcome, including data loss, device damage, or a bricked device. The author is not responsible for damage caused by your use of the program.

---

## What Breakout does

- Detects indicators that a device is enrolled in device management (work profile, device owner, provisioning remnants, restricted accounts/permissions).
- Runs a variety of diagnostics to surface management/configuration state and potential persistence mechanisms.
- Provides step-by-step playbooks to attempt removal or remediation, using Shizuku where elevated system APIs are required.
- Shows every action the playbook will run and requests explicit confirmation before performing anything.

## Notable features

- Diagnostics: readable reports on accounts, device owner/profile state, permissions, installed management apps, and policy indicators.
- Playbooks: curated sequences of actions for common locked/managed-device scenarios. Each step is explained and presented before execution.
- Shizuku integration: when available, Breakout can use Shizuku to access privileged APIs in a safer user-mediated way; Breakout never performs hidden actions.
- Clear warnings: explicit consent is required before any destructive or risky operation.

## Security & risks (read carefully)

- Breakout performs actions that can modify device state. These actions may cause data loss, disable features, or render a device unusable.
- Always run diagnostics first and review the entire playbook before confirming any destructive steps.
- Prefer testing on an emulator or a disposable device before using on a device you care about.
- Breakout does not guarantee remediation for every vendor or enterprise management configuration. Some management solutions are persistent or tied to the device hardware/firmware.

## Requirements

- Android device or emulator
- Android 8.0+ (see app/build.gradle.kts for exact compile/minSdk versions)
- Optional: Shizuku installed and configured if you want playbooks that rely on its elevated APIs

## Build & install

From the command line:

```bash
# build a debug APK
./gradlew assembleDebug

# install to a connected device or emulator
./gradlew installDebug
```

Or open the project in Android Studio and run the `app` module.

## Permissions & manifest

Before installing, review `app/src/main/AndroidManifest.xml` and the `shell/` package. The app may request permissions that allow inspection of accounts, packages, and device state. Some playbook steps require granting or enabling additional permissions.

## Where to look in the code

- app/src/main/java/com/ziddy/breakout/
  - AppViewModel.kt — main app orchestration and state
  - engine/ — diagnostic checks and remediation logic
  - shell/ — platform integrations, permission flows, and Shizuku helpers
  - data/ — local data storage and report persistence
  - ui/ — screens and user interactions
  - MainActivity.kt, BreakoutApp.kt — Android entry points

## Contributing

If you want to contribute:
1. Fork the repository
2. Create a branch (git checkout -b feat/your-feature)
3. Implement changes and document new playbooks or diagnostics
4. Open a Pull Request with a clear description and test instructions

Please be cautious when adding playbooks that perform destructive actions — document exactly what the playbook does and which devices/configurations it targets.

## Privacy & data handling

- Breakout stores diagnostic reports locally. It does not transmit data to remote servers by default.
- If you add telemetry or remote reporting, document it clearly and obtain user consent.
