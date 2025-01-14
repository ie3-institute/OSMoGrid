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
- Consider substations as type `Node` [#411](https://github.com/ie3-institute/OSMoGrid/issues/411)
- Provide documentation and query example for overpass export of OSM data [#436](https://github.com/ie3-institute/OSMoGrid/issues/436)
- Enhancing output folder with timestamp [#440](https://github.com/ie3-institute/OSMoGrid/issues/440)
- Readthedocs dependencies to dependabot [#454](https://github.com/ie3-institute/OSMoGrid/issues/454)
- Update maven repository sources [#438](https://github.com/ie3-institute/OSMoGrid/issues/438)
- Include census boundaries and `admin_level=12` for finer partitioning of osm data [#502](https://github.com/ie3-institute/OSMoGrid/issues/502)
- Scalafmt should always add trailing commas [#508](https://github.com/ie3-institute/OSMoGrid/issues/508)
- Include highway filter in example config [#513](https://github.com/ie3-institute/OSMoGrid/issues/513)
- Added Marius Staudt to list of reviewers [#516](https://github.com/ie3-institute/OSMoGrid/issues/501)
- Create `CITATION.cff` [#531](https://github.com/ie3-institute/OSMoGrid/issues/531)

### Changed
- Rely on Java 17
  - Workaround for `spotless`: Add module exports to `gradle.properties`
- Update to PSDM 4.1.0
- Replaced akka with pekko [#345](https://github.com/ie3-institute/OSMoGrid/issues/345)
- Improved `SubGridHandling` [#397](https://github.com/ie3-institute/OSMoGrid/issues/397)
- Switched from `osm4scala` to `openstreetmap.osmosis` [#409](https://github.com/ie3-institute/OSMoGrid/issues/409)
- Changed transformer input parameter to PSDM requirements [#417](https://github.com/ie3-institute/OSMoGrid/issues/417)
- Adapted run initialization [#404](https://github.com/ie3-institute/OSMoGrid/issues/404)
- Refactoring of 'getAllUniqueCombinations' to avoid nested loops [#431](https://github.com/ie3-institute/OSMoGrid/issues/431)
- Handling of boundaries that do not contain buildings [#434](https://github.com/ie3-institute/OSMoGrid/issues/434)
- Simplify `GridElements` creation [#504](https://github.com/ie3-institute/OSMoGrid/issues/504)
- Improve clustering efficiency by using faster hashcode method [#515](https://github.com/ie3-institute/OSMoGrid/issues/515)

### Fixed
- Fixed bug in `LvGridGeneratorSupport` [#388](https://github.com/ie3-institute/OSMoGrid/issues/388)
- `getConnection` in `Connections` will return an option [#392](https://github.com/ie3-institute/OSMoGrid/issues/392)
- Changed some `ParSeq` in `LvGraphGeneratorSupport` to Seq [#387](https://github.com/ie3-institute/OSMoGrid/issues/387)
- LV Coordinator dies unexpectedly [#361](https://github.com/ie3-institute/OSMoGrid/issues/361)
- Some bugs fixed [#405](https://github.com/ie3-institute/OSMoGrid/issues/405)
- Fixed number of parallel lines from zero to one [#419](https://github.com/ie3-institute/OSMoGrid/issues/419)
- Preventing unconnected nodes or subgrids [#415](https://github.com/ie3-institute/OSMoGrid/issues/415)
- Fix cases of empty id for nodes [#433](https://github.com/ie3-institute/OSMoGrid/issues/433)
- Fix osmium installation command [#498](https://github.com/ie3-institute/OSMoGrid/issues/498)
- Clustering crashes with less than two nodes [#506](https://github.com/ie3-institute/OSMoGrid/issues/506)
- Handle additional case when building a polygon from ways [#427](https://github.com/ie3-institute/OSMoGrid/issues/427)

### Removed
- Legacy Java code
  - Jacoco gradle plugin

[Unreleased]: https://github.com/ie3-institute/OSMoGrid/compare/7e598e53e333c9c1a7b19906584f0357ddf07990...HEAD
