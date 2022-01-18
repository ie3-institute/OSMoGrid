# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Read the docs capabilities
- Parsing and checking configuration
  - Input
  - Output
  - Generation
- Let `OsmoGridGuardian` initialize services
- Spawn `LvCoordinator` and trigger it
- Spawn worker pools of `LvRegionCoordinator`s and `LvGridGenerator`s
- Forward results to `ResultEventListener`
- Coordinated shut down phase
  - Post stop phase for terminated children (to shut down data connections, ...)
  - Await response from terminated children

### Changed
-  ...

### Fixed
-  ...

[Unreleased]: https://github.com/ie3-institute/OSMoGrid/compare/7e598e53e333c9c1a7b19906584f0357ddf07990...HEAD
