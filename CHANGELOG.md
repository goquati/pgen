# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed
- Resolved naming conflicts in generated table constraints by using fully qualified type names.
- Fixed JDBC and R2DBC example projects.
- Improved Gradle plugin reliability by ensuring code generation tasks always execute when requested.

## [0.42.0] - 2026-01-26

### Added
- WAL example demonstrating Write-Ahead Log functionality.

### Changed
- Switched default UUID implementation to `kotlin.uuid.Uuid` for better Kotlin standards alignment and multiplatform support.
- Moved default code generation output to the `build` directory.
- Upgraded JetBrains Exposed dependency to `1.0.0`.

### Fixed
- Stabilized and fixed WAL-related tests.

## [0.41.0] - 2026-01-25
- Initial release notes tracking started from this version.
