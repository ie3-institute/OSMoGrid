/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.model

import edu.ie3.util.osm.model.CommonOsmKey.Building
import edu.ie3.util.osm.model.OsmEntity

sealed trait SourceFilter

object SourceFilter {

  val standardBoundaryFilter: Filter =
    Filter("boundary", Set("administrative"))

  val substationFilter: Set[Filter] =
    Set(
      Filter(Building.toString, Set("transformer_tower")),
      Filter("power", Set("substation"))
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
      existingSubstationFilter: Set[Filter]
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
        landuses: Set[String]
    ): LvFilter =
      LvFilter(
        Filter("building", buildings),
        Filter("highway", highways),
        Filter("landuse", landuses),
        standardBoundaryFilter,
        substationFilter
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
        substationFilter
      )
  }
}
