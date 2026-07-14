/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.model

import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.util.osm.model.CommonOsmKey.Building
import edu.ie3.util.osm.model.OsmEntity

sealed trait SourceFilter

object SourceFilter {

  val standardBoundaryFilter: Filter =
    Filter("boundary", Set("administrative", "census"))

  val substationFilter: Set[Filter] =
    Set(
      Filter(Building.toString, Set("transformer_tower")),
      Filter("power", Set("substation")),
    )

  final case class Filter(key: String, tagValues: Set[String]) {
    def func: OsmEntity => Boolean = (osmEntity: OsmEntity) =>
      osmEntity.hasKeyValuesPairOr(key, tagValues)
  }

  final case class LvFilter(
      buildingFilter: Filter,
      highwayFilter: Filter,
      landuseFilter: Filter,
      boundaryFilter: Filter,
      existingSubstationFilter: Set[Filter],
  ) extends SourceFilter

  object LvFilter {

    /** Convenience constructor for an LvFilter
      * @param buildings
      *   Set of building values to filter for
      * @param highways
      *   Set of highways values to filter for
      * @param landuses
      *   Set of landuses values to filter for
      * @return
      *   an LvFilter with given properties
      */
    def apply(
        buildings: Set[String],
        highways: Set[String],
        landuses: Set[String],
    ): LvFilter =
      LvFilter(
        Filter("building", buildings),
        Filter("highway", highways),
        Filter("landuse", landuses),
        standardBoundaryFilter,
        substationFilter,
      )

    /** Standard constructor using empty value sets for buildings, highways and
      * landuses
      * @return
      *   a standard LvFilter
      */
    def apply(): LvFilter =
      LvFilter(
        Filter("building", Set.empty),
        Filter("highway", Set.empty),
        Filter("landuse", Set.empty),
        standardBoundaryFilter,
        substationFilter,
      )

    def apply(cfg: OsmoGridConfig.Input.Osm.Filter): LvFilter = apply(
        cfg.building.toSet,
        cfg.highway.toSet,
        cfg.landuse.toSet
      )
  }

  final case class PoiFilter(
                              home: Set[Filter],
                              supermarket: Set[Filter],
                              bbpq: Set[Filter],
                              services: Set[Filter],
                              culture: Set[Filter],
                              medicinal: Set[Filter],
                              religious: Set[Filter],
                              restaurant: Set[Filter],
                              sports: Set[Filter],
                              otherShops: Set[Filter]
                            ) extends SourceFilter

  object PoiFilter {

    given Conversion[Map[String, List[String]], Set[Filter]] = _.map { case (key, values) => Filter(key, values.toSet) }.toSet

    def apply(cfg: OsmoGridConfig.Input.Osm.POI): PoiFilter = PoiFilter(
        cfg.home,
        cfg.supermarket,
        cfg.bbpq,
        cfg.services,
        cfg.culture,
        cfg.medicinal,
        cfg.religious,
        cfg.restaurant,
        cfg.sports,
        cfg.otherShops
      )

    def apply(): PoiFilter = PoiFilter(
      Set.empty,
      Set.empty,
      Set.empty,
      Set.empty,
      Set.empty,
      Set.empty,
      Set.empty,
      Set.empty,
      Set.empty,
      Set.empty,
    )
  }

}
