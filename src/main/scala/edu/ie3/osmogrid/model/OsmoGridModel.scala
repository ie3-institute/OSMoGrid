/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.model

import edu.ie3.osmogrid.model.PbfFilter.{Filter, LvFilter}
import edu.ie3.util.osm.model.OsmEntity.{Node, Relation, Way}
import edu.ie3.util.osm.model.{CommonOsmKey, OsmContainer, OsmEntity}
import org.locationtech.jts.geom.Polygon
import edu.ie3.util.osm.model.CommonOsmKey.{Building, Highway, Landuse}
import edu.ie3.util.osm.model.OsmEntity.OsmEntityType

import scala.collection.parallel.{ParIterable, ParMap, ParSeq}

sealed trait OsmoGridModel {
  protected val filter: PbfFilter

  def +(additional: OsmoGridModel): Option[OsmoGridModel]
}

object OsmoGridModel {

  final case class LvOsmoGridModel private (
      buildings: ParSeq[OsmEntity],
      highways: ParSeq[OsmEntity],
      landuses: ParSeq[OsmEntity],
      existingSubstations: ParSeq[OsmEntity],
      private val nodes: ParMap[Long, Node],
      protected val filter: LvFilter
  ) extends OsmoGridModel {

    def node(osmId: Long): Option[Node] = nodes.get(osmId)

    def nodes(osmIds: Set[Long]): ParIterable[Node] =
      nodes.filterKeys(osmIds).values

    def allNodes(): ParSeq[Node] = nodes.values.toSeq

    // merges two lv grids, if their filter match
    // if you want to recombine two lv grids with different filters
    // you need to recreate them to ensure filter consistency
    override def +(additional: OsmoGridModel): Option[OsmoGridModel] = {
      additional match {
        case LvOsmoGridModel(
              buildings,
              highways,
              landuses,
              existingSubstations,
              nodes,
              filter
            ) if this.filter.equals(additional.filter) =>
          Some(
            LvOsmoGridModel(
              this.buildings ++ buildings,
              this.highways ++ highways,
              this.landuses ++ landuses,
              this.existingSubstations ++ existingSubstations,
              this.nodes ++ nodes,
              this.filter // filter are the same for both models
            )
          )
        case _ =>
          None
      }
    }

  }

  object LvOsmoGridModel {

    def apply(
        osmContainer: OsmContainer,
        lvFilter: LvFilter
    ): OsmoGridModel =
      new LvOsmoGridModel(
        filter(osmContainer, lvFilter.buildingFilter),
        filter(osmContainer, lvFilter.highwayFilter),
        filter(osmContainer, lvFilter.landuseFilter),
        filterOr(osmContainer, lvFilter.existingSubstationFilter),
        osmContainer.nodesMap, // todo filter nodes map based on highways, landuses, buildings and existing substations
        lvFilter
      )

    def mergeAll(models: ParSeq[OsmoGridModel]): Option[OsmoGridModel] = {
      models.headOption.flatMap { case lvHeadModel: LvOsmoGridModel =>
        if (models.forall(_.filter == lvHeadModel.filter)) {
          val (buildings, highways, landuses) = models.map {
            case lvModel: LvOsmoGridModel =>
              (lvModel.buildings, lvModel.highways, lvModel.landuses)
          }.unzip3

          val (existingSubstations, nodes) = models.map {
            case lvModel: LvOsmoGridModel =>
              (lvModel.existingSubstations, lvModel.nodes)
          }.unzip

          Some(
            LvOsmoGridModel(
              buildings.flatten,
              highways.flatten,
              landuses.flatten,
              existingSubstations.flatten,
              nodes.flatten.toMap,
              lvHeadModel.filter
            )
          )
        } else {
          None
        }
      }

    }
  }

  def filter(osmContainer: OsmContainer, filter: Filter): ParSeq[OsmEntity] =
    filterOr(osmContainer, Set(filter))

  def filterOr(
      osmContainer: OsmContainer,
      filter: Set[Filter]
  ): ParSeq[OsmEntity] = {
    val mappedFilter =
      filter.map(filter => (filter.key, filter.tagValues)).toMap
    (osmContainer.nodes ++ osmContainer.ways ++ osmContainer.relations)
      .foldLeft(ParSeq.empty) {
        case (resEntities, curEntity)
            if curEntity.hasKeysValuesPairOr(mappedFilter) =>
          resEntities :+ curEntity
        case (resEntities, _) =>
          resEntities
      }
  }

}
