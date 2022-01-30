/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.Behaviors
import edu.ie3.util.geo.GeoUtils
import edu.ie3.util.osm.model.OsmContainer.ParOsmContainer
import edu.ie3.util.osm.model.OsmEntity.Relation
import edu.ie3.util.osm.model.OsmEntity.Relation.RelationMemberType
import edu.ie3.util.osm.model.{OsmContainer, OsmEntity}
import org.locationtech.jts.geom.{Coordinate, LinearRing, Polygon}
import org.locationtech.jts.geom.impl.CoordinateArraySequence

import scala.collection.parallel.immutable.ParMap

object LvRegionCoordinator {
  sealed trait Request
  final case class Partition(
      osmContainer: OsmContainer,
      administrativeLevel: Int,
      replyTo: ActorRef[Response]
  ) extends Request

  sealed trait Response
  object Done extends Response

  def apply(
      lvGeneratorPool: ActorRef[LvGenerator.Request]
  ): Behaviors.Receive[Request] = idle

  private def idle: Behaviors.Receive[Request] = Behaviors.receive {
    case (ctx, Partition(container, administrativeLevel, replyTo)) =>
      val osmContainer = container.par()

      // TODO only extract boundaries for given administrativeLevel
      val boundaries = buildBoundaryPolygons(osmContainer)

      def filterNode(polygon: Polygon, node: OsmEntity.Node) = {
        val point = GeoUtils.buildPoint(node.latitude, node.longitude)
        polygon.covers(point)
      }

      def filterWay(polygon: Polygon, way: OsmEntity.Way) = {
        val majority = (way.nodes.size + 1) / 2
        way.nodes
          .flatMap(osmContainer.node(_))
          .filter { node =>
            filterNode(polygon, node)
          }
          .sizeCompare(majority) > 0
      }

      def filterRelation(
          polygon: Polygon,
          relation: OsmEntity.Relation
      ): Boolean = {
        val majority = (relation.members.size + 1) / 2
        relation.members
          .flatMap { member =>
            member.relationType match {
              case RelationMemberType.Node =>
                osmContainer.node(member.id)
              case RelationMemberType.Way =>
                osmContainer.way(member.id)
              case RelationMemberType.Relation =>
                osmContainer.relation(member.id)
              case RelationMemberType.Unrecognized =>
                None
            }
          }
          .filter {
            case node: OsmEntity.Node =>
              filterNode(polygon, node)
            case way: OsmEntity.Way =>
              filterWay(polygon, way)
            case relation: OsmEntity.Relation =>
              filterRelation(polygon, relation)
          }
          .sizeCompare(majority) > 0
      }

      // TODO this way might be inefficient, find better way
      // for example: resolve ids and build geographic objects at first,
      // then compare to all provided polygons
      boundaries.foreach { polygon =>
        val nodes = osmContainer.nodes.filter { case (_, node) =>
          filterNode(polygon, node)
        }
        val ways = osmContainer.ways.filter { case (_, way) =>
          filterWay(polygon, way)
        }
        val relations = osmContainer.relations.filter { case (_, relation) =>
          filterRelation(polygon, relation)
        }

        val container = ParOsmContainer(nodes, ways, relations)

        ctx.spawnAnonymous(MunicipalityCoordinator.apply(container))
      }

      /* TODO Wait for the incoming data and check, if all replies are received. */
      Behaviors.same

    case (ctx, unsupported) =>
      ctx.log.warn(s"Received unsupported message '$unsupported'.")
      Behaviors.stopped
  }

  private def buildBoundaryPolygons(osmContainer: ParOsmContainer) = {
    extractBoundaries(osmContainer).map { case (_, r) =>
      // combine all ways of a boundary relation to one sequence of coordinates
      val coordinates = r.members
        .flatMap {
          case OsmEntity.Relation.RelationMember(id, relationType, role) =>
            relationType match {
              case RelationMemberType.Way =>
                osmContainer.way(id)
              case _ => List.empty
            }
        }
        .flatMap { el =>
          osmContainer.nodes(el.nodes)
        }
        .flatten
        .map { node =>
          GeoUtils.buildCoordinate(node.latitude, node.longitude)
        }
        .toArray

      GeoUtils.buildPolygon(coordinates)
    }
  }

  /** Returns a list of boundary relations consisting of counties ("Gemeinden")
    * and independent towns ("Kreisfreie Städte").
    *
    * Should work in most places in Germany.
    * @param osmContainer
    *   the OSM model
    * @return
    *   list of boundary relations
    */
  private def extractBoundaries(
      osmContainer: ParOsmContainer
  ) = {
    val relations = osmContainer.relations

    val boundaries = relations.filter { case (id, relation) =>
      relation.tags.get("boundary").contains("administrative")
    }

    val municipalities =
      boundaries.filter { case (id, relation) =>
        relation.tags.get("admin_level").contains("8")
      }

    // independent towns with tag de:place=city https://forum.openstreetmap.org/viewtopic.php?id=21788
    val independentCities = boundaries.filter { case (id, relation) =>
      relation.tags.get("admin_level").contains("6") && relation.tags
        .get("de:place")
        .contains("city")
    }

    municipalities ++ independentCities
  }

}
