# Changelog

All notable changes to this project are documented here.

## [0.1.0] - 2026-10-22

### Added

- Android app with CameraX preview + ImageCapture.
- Single-ROI and full-image sliding-window crack detection.
- TFLite FP16 inference (1024×1024, float32 I/O, 4 CPU threads).
- Asynchronous capture pipeline: capture → decode/rotate → inference queue → results.
- Session history (10 results), previous/next browsing, PNG export.
- File logging with rotation (`files/logs/app.log`).
- CSV metrics with model/device/timing/memory fields.
- Model metadata with SHA256 displayed in the app.
- Global crash handler writing local crash logs.
- Gradle Wrapper.
- GitHub Actions debug build.
- GitHub Release workflow template.
- README, architecture, development and benchmark docs.
- Apache-2.0 license and third-party notice.

### Known issues

- Model accuracy is below specialized SOTA crack models (CrackMamba / SCSegamba).
- Full-image sliding-window mode can take tens of seconds to minutes on low-end phones.
- Only Android is supported.
- Debug signing only; not ready for app-store distribution.
- Real-device benchmark across multiple phones is pending.
