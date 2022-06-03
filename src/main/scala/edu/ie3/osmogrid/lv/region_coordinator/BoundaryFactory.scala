/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv.region_coordinator

import edu.ie3.osmogrid.io.input.BoundaryAdminLevel
import edu.ie3.osmogrid.model.OsmoGridModel.{EnhancedOsmEntity, LvOsmoGridModel}
import edu.ie3.util.geo.GeoUtils
import edu.ie3.util.osm.model.OsmEntity
import edu.ie3.util.osm.model.OsmEntity.Relation.RelationMemberType
import org.locationtech.jts.geom.Polygon

import scala.collection.parallel.ParMap
import scala.util.{Failure, Try}

object BoundaryFactory {

  /** Id of the boundary relation
    */
  type AreaKey = Long

  /** Creating boundary polygons from corresponding relations at given
    * administrative level
    *
    * @param osmoGridModel
    *   the data model to take boundaries from
    * @param administrativeLevel
    *   the administrative level for which boundary polygons should be created
    * @return
    *   a map of boundary id to polygon
    */
  def buildBoundaryPolygons(
      osmoGridModel: LvOsmoGridModel,
      administrativeLevel: BoundaryAdminLevel.Value
  ): ParMap[AreaKey, Polygon] = {
    osmoGridModel.boundaries
      .filter {
        case EnhancedOsmEntity(
              entity: OsmEntity,
              _
            ) =>
          entity match {
            case relation: OsmEntity.Relation =>
              relation.tags
                .get("admin_level")
                .contains(administrativeLevel.id.toString)
            case _ => false
          }
      }
      .map { enhancedEntity =>
        enhancedEntity.entity.id -> buildBoundaryPolygon(enhancedEntity)
      }
      .toMap
  }

  /** Creates a boundary polygon for given boundary relation.
    *
    * Boundary relations consist of one or more ways, of which two consecutive
    * ways share the first and last node. The nodes in ways can be ordered in
    * both directions, thus all permutations have to be checked.
    *
    * @param enhancedRelation
    *   the relation wrapped in an [[EnhancedOsmEntity]]
    * @return
    *   the boundary polygon
    */
  private def buildBoundaryPolygon(
      enhancedRelation: EnhancedOsmEntity
  ): Polygon = {
    val relation = enhancedRelation.entity match {
      case relation: OsmEntity.Relation => relation
      case other =>
        throw new RuntimeException(
          s"Wrong entity type ${other.getClass}, Relation is required"
        )
    }

    val coordinates = (relation.members
      .flatMap {
        // Filter for ways only
        case OsmEntity.Relation.RelationMember(id, relationType, _) =>
          relationType match {
            case RelationMemberType.Way =>
              enhancedRelation.way(id)
            case _ =>
              // We only want ways, discard all other types
              None
          }
      }
      .foldLeft(Seq.empty[Long]) { case (existingNodes, currentWay) =>
        addWayNodesToPolygonSequence(existingNodes, currentWay).recoverWith {
          case exc =>
            Failure(
              new RuntimeException(
                s"Could not create Polygon from relation ${relation.id}",
                exc
              )
            )
        }.get
      } match {
      // Sanity check: in the end, first node should equal the last node
      case nodes
          if nodes.headOption
            .zip(nodes.lastOption)
            .exists(firstLast => firstLast._1 != firstLast._2) =>
        throw new RuntimeException(
          s"First node should be last in boundary relation ${relation.id}."
        )
      case nodes if nodes.isEmpty =>
        throw new RuntimeException(
          s"Empty boundary relation ${relation.id}."
        )
      case nodes => nodes
    })
      .map { node =>
        // Turn node ids into nodes
        enhancedRelation
          .node(node)
          .getOrElse(
            throw new RuntimeException(
              s"Node $node not found in enhanced relation $enhancedRelation"
            )
          )
      }
      .map { node =>
        // Turn nodes into coordinates
        GeoUtils.buildCoordinate(node.latitude, node.longitude)
      }
      .toArray

    GeoUtils.buildPolygon(coordinates)
  }

  private def addWayNodesToPolygonSequence(
      existingNodes: Seq[AreaKey],
      currentWay: OsmEntity.Way
  ): Try[Seq[AreaKey]] = Try {
    // Construct one single sequence of nodes by joining the ways.
    // Each way can be ordered in correct or in reverse order
    val currentNodes = currentWay.nodes
    existingNodes.headOption.zip(existingNodes.lastOption) match {
      case Some((existingFirst, existingLast)) =>
        currentNodes.headOption
          .zip(currentNodes.lastOption)
          .map { case (currentFirst, currentLast) =>
            // Run through a bunch of cases. In the end, we want [a, b, c, d, e]
            if (existingLast == currentFirst)
              // [a, b, c] and [c, d, e]
              // All in correct order
              existingNodes ++ currentNodes.drop(1)
            else if (existingLast == currentLast)
              // [a, b, c] and [e, d, c]
              // Additional sequence needs to be flipped
              existingNodes ++ currentNodes.reverse.drop(1)
            else if (existingFirst == currentFirst)
              // [c, b, a] and [c, d, e]
              // Existing sequence in wrong order:
              // this should only happen if we have only added one
              // sequence so far and that sequence was in wrong order
              existingNodes.reverse ++ currentNodes.drop(1)
            else if (existingFirst == currentLast)
              // [c, b, a] and [e, d, c]; a != e since we already covered this with first case
              // Existing sequence in wrong order, similar to above
              // but additional sequence has be flipped as well
              existingNodes.reverse ++ currentNodes.reverse.drop(1)
            else
              throw new RuntimeException(
                s"Last node $existingLast was not found in way ${currentWay.id}"
              )
          }
          .getOrElse(
            // Current way is empty, carry on with old sequence
            existingNodes
          )
      case None =>
        //  No nodes added yet, just put current ones in place
        currentNodes
    }
  }
}
