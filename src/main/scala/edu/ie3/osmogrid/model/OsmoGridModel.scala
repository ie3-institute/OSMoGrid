/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.model

import edu.ie3.osmogrid.model.PbfFilter.{Filter, LvFilter}
import edu.ie3.util.osm.model.OsmEntity.{Node, Relation, Way}
import edu.ie3.util.osm.model.{CommonOsmKey, OsmContainer, OsmEntity}
import edu.ie3.util.osm.model.OsmContainer.ParOsmContainer
import org.locationtech.jts.geom.Polygon
import edu.ie3.util.osm.model.CommonOsmKey.{Building, Highway, Landuse}
import edu.ie3.util.osm.model.OsmEntity.Relation.RelationMemberType

import scala.collection.parallel.immutable.ParVector
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
      boundaries: ParSeq[OsmEntity],
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
              boundaries,
              existingSubstations,
              nodes,
              filter
            ) if this.filter.equals(additional.filter) =>
          Some(
            LvOsmoGridModel(
              this.buildings ++ buildings,
              this.highways ++ highways,
              this.landuses ++ landuses,
              this.boundaries ++ boundaries,
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
        osmContainer: ParOsmContainer,
        lvFilter: LvFilter,
        filterNodes: Boolean = true
    ): LvOsmoGridModel =
      val buildings = filter(osmContainer, lvFilter.buildingFilter)
      val highways = filter(osmContainer, lvFilter.highwayFilter)
      val landuses = filter(osmContainer, lvFilter.landuseFilter)
      val boundaries = filter(osmContainer, PbfFilter.standardBoundaryFilter)
      val substations =
        filterOr(osmContainer, PbfFilter.substationFilter)

      val nodes = createNodes(
        osmContainer.nodes,
        ParVector(buildings, highways, landuses, boundaries, substations),
        filterNodes
      )

      new LvOsmoGridModel(
        buildings,
        highways,
        landuses,
        boundaries,
        substations,
        nodes,
        lvFilter
      )

    def mergeAll(
        models: ParSeq[OsmoGridModel],
        filterNodes: Boolean = true
    ): Option[OsmoGridModel] = {
      models.headOption.flatMap { case lvHeadModel: LvOsmoGridModel =>
        if (models.forall(_.filter == lvHeadModel.filter)) {
          val (buildings, highways, landuses) = models.map {
            case lvModel: LvOsmoGridModel =>
              (lvModel.buildings, lvModel.highways, lvModel.landuses)
          }.unzip3 match {
            case (buildings, highways, landuses) =>
              (buildings.flatten, highways.flatten, landuses.flatten)
          }

          val (boundaries, existingSubstations, unfilteredNodes) = models.map {
            case lvModel: LvOsmoGridModel =>
              (lvModel.boundaries, lvModel.existingSubstations, lvModel.nodes)
          }.unzip3 match {
            case (boundaries, existingSubstations, unfilteredNodes) =>
              (
                boundaries.flatten,
                existingSubstations.flatten,
                unfilteredNodes.flatten.toMap
              )
          }
          val nodes = createNodes(
            unfilteredNodes,
            ParVector(
              buildings,
              highways,
              landuses,
              boundaries,
              existingSubstations
            ),
            filterNodes
          )
          Some(
            LvOsmoGridModel(
              buildings,
              highways,
              landuses,
              boundaries,
              existingSubstations,
              nodes,
              lvHeadModel.filter
            )
          )
        } else {
          None
        }
      }

    }

    private def createNodes(
        nodesMap: ParMap[Long, Node],
        entitiesWithNodes: ParSeq[ParSeq[OsmEntity]],
        filterNodes: Boolean
    ) =
      if (filterNodes) {
        val nodeIds: Set[Long] = entitiesWithNodes.flatten
          .flatMap {
            case node: Node =>
              Seq(node.id)
            case way: Way =>
              way.nodes
            case relation: Relation =>
              relation.members
                .filter(_.relationType match {
                  case RelationMemberType.Node =>
                    true
                  case _ => false
                })
                .map(_.id)
          }
          .seq
          .toSet
        nodesMap.filterKeys(nodeIds)
      } else {
        nodesMap
      }

  }

  def filter(osmContainer: ParOsmContainer, filter: Filter): ParSeq[OsmEntity] =
    filterOr(osmContainer, Set(filter))

  def filterOr(
      osmContainer: ParOsmContainer,
      filter: Set[Filter]
  ): ParSeq[OsmEntity] = {
    val mappedFilter =
      filter.map(filter => (filter.key, filter.tagValues)).toMap
    (osmContainer.nodes.values ++ osmContainer.ways.values ++ osmContainer.relations.values)
      .foldLeft(ParSeq.empty) {
        case (resEntities, curEntity)
            if curEntity.hasKeysValuesPairOr(mappedFilter) =>
          resEntities :+ curEntity
        case (resEntities, _) =>
          resEntities
      }
  }

}
