/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv.region_coordinator

import com.typesafe.scalalogging.LazyLogging
import edu.ie3.osmogrid.model.OsmoGridModel.{EnhancedOsmEntity, LvOsmoGridModel}
import edu.ie3.util.geo.GeoUtils
import edu.ie3.util.osm.model.OsmEntity
import edu.ie3.util.osm.model.OsmEntity.Relation.RelationMemberType
import org.locationtech.jts.geom.Polygon
import edu.ie3.osmogrid.lv.region_coordinator.EntityAllocationStrategy.{
  AssignByMax,
  AssignToAll
}

import scala.collection.parallel.{ParMap, ParSeq}

object OsmoGridModelPartitioner extends LazyLogging {

  /** Id of the boundary relation
    */
  type AreaKey = Long

  /** Assign all entities of given OsmoGridModel to the given areas. Depending
    * on the type of entity they are assigned to more than one area.
    *
    * Entities are assigned to boundaries by comparing the amount of associated
    * points that are covered by each boundary. When using the allocation
    * strategy [[AssignByMax]], the entity is assigned to the boundary with the
    * most covered points.
    *
    * @param osmoGridModel
    *   the OsmoGridModel to partition
    * @param areas
    *   the areas that the entities should be assigned to
    * @return
    *   a map from area id (id of the boundary relation) to OsmoGridModel
    */
  def partition(
      osmoGridModel: LvOsmoGridModel,
      areas: ParMap[AreaKey, Polygon]
  ): ParMap[AreaKey, LvOsmoGridModel] = {
    val buildings = assign(
      osmoGridModel.buildings,
      areas,
      AssignByMax
    )
    val highways = assign(
      osmoGridModel.highways,
      areas,
      AssignToAll
    )
    val landuses = assign(
      osmoGridModel.landuses,
      areas,
      AssignToAll
    )
    val boundaries = assign(
      osmoGridModel.boundaries,
      areas,
      AssignByMax
    )
    val existingSubstations = assign(
      osmoGridModel.existingSubstations,
      areas,
      AssignByMax
    )

    areas.keys.map { areaId =>
      areaId ->
        LvOsmoGridModel(
          buildings.getOrElse(areaId, ParSeq.empty),
          highways.getOrElse(areaId, ParSeq.empty),
          landuses.getOrElse(areaId, ParSeq.empty),
          boundaries.getOrElse(areaId, ParSeq.empty),
          existingSubstations.getOrElse(areaId, ParSeq.empty),
          osmoGridModel.filter
        )
    }.toMap
  }

  private def assign(
      enhancedEntities: ParSeq[EnhancedOsmEntity],
      areas: ParMap[AreaKey, Polygon],
      allocationStrategy: EntityAllocationStrategy
  ): ParMap[AreaKey, ParSeq[EnhancedOsmEntity]] = {

    enhancedEntities
      .flatMap { entity =>
        assign(entity, areas, allocationStrategy).map(_ -> entity)
      }
      .groupBy(_._1)
      .map { case (areaId, entities) =>
        areaId -> entities.map(_._2)
      }
  }

  private def assign(
      enhancedEntity: EnhancedOsmEntity,
      areas: ParMap[AreaKey, Polygon],
      allocationStrategy: EntityAllocationStrategy
  ): Iterable[AreaKey] = {

    val entityVotes = vote(enhancedEntity.entity, enhancedEntity, areas)

    allocationStrategy match {
      case AssignToAll =>
        entityVotes.keys
      case AssignByMax =>
        entityVotes
          .maxByOption(_._2)
          .map { case (areaKey, voteCount) =>
            if (voteCount == 0)
              logger warn s"Entity ${enhancedEntity.entity} is not covered by any given area " +
                s"(but is assigned to area $areaKey nonetheless)"

            areaKey
          }
          .map(Iterable.single)
          .getOrElse(Iterable.empty)
    }
  }

  private def vote(
      entity: OsmEntity,
      enhancedEntity: EnhancedOsmEntity,
      areas: ParMap[AreaKey, Polygon]
  ): Map[AreaKey, Int] =
    entity match {
      case node: OsmEntity.Node =>
        vote(node, areas)
      case way: OsmEntity.Way =>
        vote(way, enhancedEntity, areas)
      case relation: OsmEntity.Relation =>
        vote(relation, enhancedEntity, areas)
    }

  private def vote(
      relation: OsmEntity.Relation,
      enhancedEntity: EnhancedOsmEntity,
      areas: ParMap[AreaKey, Polygon]
  ): Map[AreaKey, Int] =
    relation.members
      .flatMap { member =>
        member.relationType match {
          case RelationMemberType.Node =>
            enhancedEntity.node(member.id)
          case RelationMemberType.Way =>
            enhancedEntity.way(member.id)
          case RelationMemberType.Relation =>
            enhancedEntity.relation(member.id)
          case RelationMemberType.Unrecognized =>
            None
        }
      }
      .flatMap { entity =>
        vote(entity, enhancedEntity, areas).toSeq
      }
      .groupBy(_._1)
      .map { case areaId -> votes =>
        areaId -> votes.map(_._2).sum
      }

  private def vote(
      way: OsmEntity.Way,
      enhancedEntity: EnhancedOsmEntity,
      areas: ParMap[AreaKey, Polygon]
  ): Map[AreaKey, Int] =
    way.nodes
      .flatMap(enhancedEntity.node)
      .flatMap { node =>
        vote(node, areas)
      }
      .groupBy(_._1)
      .map { case areaId -> votes =>
        areaId -> votes.map(_._2).sum
      }

  private def vote(
      node: OsmEntity.Node,
      areas: ParMap[AreaKey, Polygon]
  ): Map[AreaKey, Int] = {
    val point = GeoUtils.buildPoint(node.latitude, node.longitude)
    areas.iterator
      .collectFirst {
        case areaId -> polygon if polygon.covers(point) =>
          Map(areaId -> 1)
      }
      .getOrElse(Map.empty)
  }
}
