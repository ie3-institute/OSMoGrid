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
- Let `OsmoGridGuardian` handle multiple runs and spawn children accordingly
- A `RunGuardian` takes care of a distinct simulation run and spawns all its needed services
  - Spawn an `InputDataProvider` and a `ResultListener`(if required) per run
  - Spawn `LvCoordinator` and trigger it
- A `LvCoordinator` coordinates the generation of the low voltage grid level
  - Acquires needed osm and asset input data
  - Starts the process chain by spawning a `LvRegionCoordinator`
- Coordinated shut down phase
  - Only terminate OSMoGrid internal result event listener and let additional listeners alive
  - Post stop phase for terminated children (to shut down data connections, ...)
  - Await response from terminated children
- `SubGridHandling` takes care of merging the created sub grids and adapting the sub grid numbers to ensure uniqueness
- A `ResultListener` handles given grid results
  - Receives a JointGridContainer
  - Writes grid data into csv files
- Added `MvCoordinator`
- Switched from `akka` to `pekko`
- Adding the clustering of low voltage grids

### Changed
- Rely on Java 17
  - Workaround for `spotless`: Add module exports to `gradle.properties`
- Update to PSDM 4.1.0
- Replaced akka with pekko [#345](https://github.com/ie3-institute/OSMoGrid/issues/345)
- Fixed bug in `LvGridGeneratorSupport` [#388](https://github.com/ie3-institute/OSMoGrid/issues/388)

### Removed
- Legacy Java code
  - Jacoco gradle plugin

[Unreleased]: https://github.com/ie3-institute/OSMoGrid/compare/7e598e53e333c9c1a7b19906584f0357ddf07990...HEAD
