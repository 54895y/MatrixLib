# Changelog

All notable changes to MatrixLib will be documented in this file.

The format is based on Keep a Changelog, and this project follows Semantic Versioning for release tags.

## [1.5.0] - 2026-04-05

### Added

- Added stable API documentation and KDoc coverage for the MatrixLib public namespace.
- Added Dokka Javadoc generation and `javadocJar` output.

### Changed

- Raised MatrixLib to the new stable API baseline for the Matrix plugin series.
- Documented the public namespace structure for action, text, YAML, menu, hologram, metrics, and updater APIs.

### Validated

- Verified `bash ./gradlew build`.
- Verified `bash ./gradlew generateJavadoc`.

## [1.4.0] - 2026-04-05

### Added

- Added shared GitHub Releases updater with admin approval workflow.
- Added centralized registration for MatrixAuth, MatrixCook, MatrixShop, and MatrixStorage.
- Added `/matrixlib update ...` commands and updater config resource.

### Changed

- MatrixLib now manages Matrix-series update checks and downloads to `plugins/update/`.
- Updated docs and README to explain the approval-based update flow.

### Validated

- Verified `bash ./gradlew build`.
- Verified downstream builds for MatrixAuth, MatrixCook, MatrixShop, and MatrixStorage against `matrixlibApiVersion=1.4.0`.

## [1.3.0] - 2026-04-05

### Added

- Added shared `MatrixBStats` API to centralize Matrix-series telemetry registration.
- Added built-in MatrixLib telemetry charts for currency topology reporting.

### Changed

- MatrixLib now shades and owns the `bStats` runtime for downstream Matrix plugins.
- MatrixAuth, MatrixCook, MatrixShop, and MatrixStorage now register telemetry through the MatrixLib API instead of direct `Metrics` access.
- Updated public docs, README entry points, and release materials for the new telemetry layer.

### Validated

- Verified `bash ./gradlew build`.
- Verified downstream builds for MatrixAuth, MatrixCook, MatrixShop, and MatrixStorage against `matrixlibApiVersion=1.3.0`.
