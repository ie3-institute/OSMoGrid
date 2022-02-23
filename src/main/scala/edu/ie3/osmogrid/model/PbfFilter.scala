/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.model

import edu.ie3.util.osm.model.CommonOsmKey.Building
import edu.ie3.util.osm.model.OsmEntity

sealed trait PbfFilter

object PbfFilter {

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
  ) extends PbfFilter

}
