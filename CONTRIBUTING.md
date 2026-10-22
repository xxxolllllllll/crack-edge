# Contributing

Thanks for your interest in CrackEdge.

## Before you start

This is a research prototype. Please open an issue before large changes so we
can agree on scope.

## Build

```bash
cd android
./gradlew assembleDebug
```

Requirements:

- JDK 17
- Android SDK 34
- Android 7.0 (API 24) or newer for running

## Test on a device

```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb logcat -s CrackEdge
```

Please include in bug reports:

- phone model and Android/HarmonyOS version;
- app version / commit;
- model asset and SHA256 prefix (shown at the bottom of the app);
- steps to reproduce;
- `files/logs/app.log` and, if present, `files/logs/crash_*.txt`.

## Pull requests

- Keep changes focused.
- Follow the existing Java style (4 spaces, no wildcard imports).
- Update `CHANGELOG.md` for user-visible changes.
- Update docs when changing architecture, model I/O or metrics fields.
- Do not commit `build/`, `.gradle/`, `local.properties`, keystores, APKs, logs or metrics.

## Model changes

If you change the model:

- keep the input/output contract (`[1,1024,1024,3]` → `[1,1024,1024,2]`) or update the app accordingly;
- update `model/README.md` and `model/SHA256SUMS`;
- note the checkpoint, quantization format and evaluation protocol;
- do not compare numbers evaluated on images that overlap the training set.
