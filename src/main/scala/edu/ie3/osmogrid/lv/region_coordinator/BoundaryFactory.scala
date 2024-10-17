/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv.region_coordinator

import edu.ie3.osmogrid.io.input.BoundaryAdminLevel.BoundaryAdminLevelValue
import edu.ie3.osmogrid.model.OsmoGridModel.{EnhancedOsmEntity, LvOsmoGridModel}
import edu.ie3.util.geo.GeoUtils
import edu.ie3.util.osm.model.OsmEntity
import edu.ie3.util.osm.model.OsmEntity.Relation.RelationMemberType
import org.locationtech.jts.geom.{Coordinate, Polygon}

import scala.collection.mutable.ListBuffer
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
    *   a map of boundary id to polygons (can be multiple polygons per key)
    */
  def buildBoundaryPolygons(
      osmoGridModel: LvOsmoGridModel,
      administrativeLevel: BoundaryAdminLevelValue
  ): ParMap[AreaKey, Seq[Polygon]] = {
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
                .contains(administrativeLevel.osmLevel.toString)
            case _ => false
          }
      }
      .map { enhancedEntity =>
        enhancedEntity.entity.id -> buildBoundaryPolygon(enhancedEntity)
      }
      .to(ParMap)
  }

  /** Creates boundary polygons for given boundary relation.
    *
    * Boundary relations consist of one or more ways, of which two consecutive
    * ways share the first and last node. The nodes in ways can be ordered in
    * both directions, thus all permutations have to be checked.
    *
    * @param enhancedRelation
    *   the relation wrapped in an [[EnhancedOsmEntity]]
    * @return
    *   the boundary polygons
    */

  private def buildBoundaryPolygon(
      enhancedRelation: EnhancedOsmEntity
  ): List[Polygon] = {

    // split seq of nodes when first nodes appears again. Repeat till it matches the last one

    val relation = enhancedRelation.entity match {
      case relation: OsmEntity.Relation => relation
      case other =>
        throw new RuntimeException(
          s"Wrong entity type ${other.getClass}, Relation is required"
        )
    }
    val allNodesOfPolygonsSeq: Seq[AreaKey] = relation.members
      .flatMap {
        case OsmEntity.Relation.RelationMember(id, RelationMemberType.Way, _) =>
          enhancedRelation.way(id)
        case _ => None
      }
      .foldLeft(Seq.empty[Seq[AreaKey]]) { case (existingNodes, currentWay) =>
        val resultNodes: Seq[AreaKey] =
          addWayNodesToPolygonSequence(existingNodes.flatten, currentWay)
            .recoverWith { case exc =>
              Failure(
                new RuntimeException(
                  s"Could not create Polygon from relation ${relation.id}",
                  exc
                )
              )
            }
            .getOrElse(
              // Current way is empty, carry on with old sequence
              existingNodes.flatten
            )

        (Seq(resultNodes))
      }
      .flatten

    val listOfSeq: Map[Int, Seq[AreaKey]] = splitByKey(allNodesOfPolygonsSeq)
    val polygonList: ListBuffer[Polygon] = ListBuffer.empty[Polygon]

    listOfSeq.values.foreach { seq =>
      val latLonCoordinates: Seq[Coordinate] = seq.flatMap { node =>
        enhancedRelation
          .node(node)
          .map(n => GeoUtils.buildCoordinate(n.latitude, n.longitude))
      }

      // Ensure the LinearRing is closed
      val closedCoordinates =
        if (
          latLonCoordinates.nonEmpty &&
          latLonCoordinates.headOption == latLonCoordinates.lastOption
        )
          latLonCoordinates
        else
          throw new RuntimeException(
            s"First node should be last in boundary relation ${relation.id}."
          )

      val polygon: Polygon = GeoUtils.buildPolygon(closedCoordinates.toArray)
      polygonList += polygon
    }

    polygonList.toList

  }

  /** Split node sequences of polygons into parts. If the sequence can't be
    * split because it represents only one polygon, the sequence is returned
    * with the first node added at the end to close the polygon.
    *
    * @param seq
    *   Sequence to split
    * @tparam A
    *   type of sequence
    * @return
    *   Indexed map with the split sequences
    */

  private def splitByKey[A](seq: Seq[A]): Map[Int, Seq[A]] = {
    val result = seq
      .foldLeft((Map.empty[Int, Seq[A]], Map.empty[A, Int], 0)) {
        case ((acc, indexes, splitIdx), elem) =>
          indexes.get(elem) match {
            case Some(prevIdx) =>
              // Element has been seen before
              val newSeq = seq.slice(prevIdx, splitIdx + 1)
              (
                acc + (acc.size + 1 -> newSeq),
                indexes + (elem -> splitIdx),
                splitIdx + 1
              )
            case None =>
              // Element has not been seen before
              (
                acc,
                indexes + (elem -> splitIdx),
                splitIdx + 1
              )
          }
      }
      ._1

    // If the result map is empty, return the whole sequence with the first element appended at the end
    // (to close the polygon)
    if (result.isEmpty) {
      seq.headOption match {
        case Some(firstElem) => Map(1 -> (seq :+ firstElem))
        case None =>
          Map(1 -> seq)
      }
    } else {
      result
    }
  }

  private def addWayNodesToPolygonSequence(
      existingNodes: Seq[AreaKey],
      currentWay: OsmEntity.Way
  ): Try[Seq[AreaKey]] = Try {
    // Construct one single sequence of nodes by joining the ways.
    // Each way can be ordered in correct or in reverse order
    val currentNodes: Seq[AreaKey] = currentWay.nodes
    val result = existingNodes.headOption.zip(existingNodes.lastOption) match {
      case Some((existingFirst: AreaKey, existingLast: AreaKey)) =>
        if (existingFirst.equals(existingLast)) {
          existingNodes ++ currentNodes
        } else {

          currentNodes.headOption
            .zip(currentNodes.lastOption)
            .map { case (currentFirst, currentLast) =>
              // Run through a bunch of cases. In the end, we want [a, b, c, d, e ... a]
              if (
                existingFirst == currentFirst && existingLast == currentLast
              ) {
                // [a, b, c] and [a, d, c]
                // Additional sequence needs to be flipped
                // Polygon will be closed by this way
                (existingNodes ++ currentNodes.reverse.drop(1))
              } else if (
                existingFirst == currentLast && existingLast == currentFirst
              ) {
                // [a, b, c] and [c, d, a]
                // All in correct order
                // Polygon will be closed by this way
                (existingNodes ++ currentNodes.drop(1))
              } else if (existingLast == currentFirst)
                // [a, b, c] and [c, d, e]
                // All in correct order
                (existingNodes ++ currentNodes.drop(1))
              else if (existingLast == currentLast)
                // [a, b, c] and [e, d, c]
                // Additional sequence needs to be flipped
                (existingNodes ++ currentNodes.reverse.drop(1))
              else if (existingFirst == currentFirst)
                // [c, b, a] and [c, d, e]
                // Existing sequence in wrong order:
                // this should only happen if we have only added one
                // sequence so far and that sequence was in wrong order
                (existingNodes.reverse ++ currentNodes.drop(1))
              else if (existingFirst == currentLast)
                // [c, b, a] and [e, d, c]; a != e since we already covered this with first case
                // Existing sequence in wrong order, similar to above
                // but additional sequence has to be flipped as well
                (existingNodes.reverse ++ currentNodes.reverse.drop(1))
              else
                throw new RuntimeException(
                  s"Last node $existingLast was not found in way ${currentWay.id}"
                )
            }
            .getOrElse(
              // Current way is empty, carry on with old sequence
              (existingNodes)
            )
        }
      case None =>
        //  No nodes added yet, just put current ones in place
        (currentNodes)
    }
    (result)
  }
}
