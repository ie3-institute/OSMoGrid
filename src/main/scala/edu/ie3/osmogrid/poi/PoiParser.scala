/*
 * © 2026. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.poi

import edu.ie3.osmogrid.ActorStopSupportStateless
import edu.ie3.osmogrid.exception.OsmDataException
import edu.ie3.osmogrid.io.input.FilterType.POI
import edu.ie3.osmogrid.io.input.{InputDataEvent, InputResponse, RepOsm, ReqOsm}
import edu.ie3.osmogrid.io.output.{PoiResult, ResultListenerProtocol}
import edu.ie3.osmogrid.model.OsmoGridModel
import edu.ie3.osmogrid.model.OsmoGridModel.{EnhancedOsmEntity, PoiModel}
import edu.ie3.util.geo.GeoUtils
import edu.ie3.util.geo.RichGeometries.calcAreaOnEarth
import edu.ie3.util.osm.model.OsmEntity
import edu.ie3.util.osm.model.OsmEntity.Relation.{
  RelationMember,
  RelationMemberType,
}
import edu.ie3.util.osm.model.OsmEntity.Way.{ClosedWay, OpenWay}
import edu.ie3.util.osm.model.OsmEntity.{Node, Relation}
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.locationtech.jts.geom.*
import org.slf4j.Logger
import tech.units.indriya.unit.Units
import utils.OsmoGridUtils.safeBuildPolygon

import scala.collection.mutable
import scala.collection.parallel.ParSeq

object PoiParser extends ActorStopSupportStateless {

  private val factory = GeoUtils.DEFAULT_GEOMETRY_FACTORY

  sealed trait PoiRequest

  object StartParsing extends PoiRequest

  def apply(resultListener: ActorRef[ResultListenerProtocol])(using
      inputDataProvider: ActorRef[InputDataEvent]
  ): Behavior[PoiRequest | InputResponse] = Behaviors.receive {
    case (ctx, StartParsing) =>
      ctx.log.info(s"Requesting points of interest ...")

      inputDataProvider ! ReqOsm(ctx.self, POI)
      Behaviors.same

    case (ctx, RepOsm(osmModel)) =>
      osmModel match {
        case poiModel: PoiModel =>
          ctx.log.info(s"Received osm entities. Try to parse them ...")
          val elements = parseElements(poiModel)(using ctx.log)

          resultListener ! PoiResult(elements)

          stopBehavior

        case other =>
          ctx.log.error(s"Cannot parse POIs from ${other.getClass}.")
          Behaviors.stopped
      }
  }

  def parseElements(
      poiModel: PoiModel
  )(using log: Logger): Iterable[PoiElement] =
    poiModel.asMap.flatMap { case (poiType, entities) =>
      parseElements(poiType, entities)
    }

  private def parseElements(
      poiType: String,
      elements: ParSeq[EnhancedOsmEntity],
  )(using log: Logger): Iterable[PoiElement] = {
    val (nodes, others) = elements.partition {
      _.entity match {
        case _: Node => true
        case _       => false
      }
    }

    val parsed = others.map { enhancedEntity =>
      val (subNodes, otherSubEntities) = {
        val nodeBuilder = Map.newBuilder[Long, Node]
        val otherBuilder = Map.newBuilder[Long, OsmEntity]

        enhancedEntity.subEntities.foreach { case (id, entity) =>
          entity match {
            case node: Node => nodeBuilder += id -> node
            case other      => otherBuilder += id -> other
          }
        }

        (nodeBuilder.result, otherBuilder.result)
      }

      enhancedEntity.entity match {
        case way: (ClosedWay | OpenWay) =>
          val buildingPolygon = way match {
            case closedWay: ClosedWay => safeBuildPolygon(closedWay, subNodes)
            case openWay: OpenWay     => safeBuildPolygon(openWay, subNodes)
          }

          val point = buildingPolygon.getCentroid.getCoordinate
          val (lat, lon, area) =
            (point.y, point.x, buildingPolygon.calcAreaOnEarth)

          PoiElement(
            enhancedEntity.entity.id,
            poiType,
            lat,
            lon,
            area.to(Units.SQUARE_METRE).getValue.doubleValue(),
          )

        case relation: Relation =>
          val buildingPolygon =
            getPolygon(relation.members, subNodes, otherSubEntities)

          val point = buildingPolygon.getInteriorPoint.getCoordinate
          val (lat, lon) = (point.y, point.x)

          PoiElement(
            enhancedEntity.entity.id,
            poiType,
            lat,
            lon,
            buildingPolygon.calcArea,
          )
      }
    }

    val averageSize = parsed.map(_.size).sum / parsed.size

    val parsedNodes = nodes.map { enhancedEntity =>
      enhancedEntity.entity match {
        case Node(id, latitude, longitude, tags, metaInformation) =>
          PoiElement(id, poiType, latitude, longitude, averageSize)
      }
    }

    (parsed ++ parsedNodes).seq
  }

  private def getPolygon(
      relationMembers: Seq[RelationMember],
      subNodes: Map[Long, Node],
      otherSubEntities: Map[Long, OsmEntity],
  )(using log: Logger): MultiPolygon = {
    val outerRings = Seq.newBuilder[Seq[Coordinate]]
    val innerRings = Seq.newBuilder[Seq[Coordinate]]

    relationMembers.foreach {
      case RelationMember(id, RelationMemberType.Way, role) =>
        otherSubEntities.get(id) match {
          case Some(way: OsmEntity.Way) =>
            val coords = getCoordinates(way.nodes, subNodes)

            role match {
              case "outer" =>
                outerRings += coords
              case "inner" =>
                innerRings += coords
              case other =>
                outerRings += coords
            }

          case other =>
            log.warn(
              s"Expected a way for role '$role' and id '$id' but got: $other"
            )
        }
      case _ => // ignore non-way members
    }

    val inner = mergeWays(innerRings.result).filter(_.isValid).toArray

    val polygons = mergeWays(outerRings.result).map { outer =>
      factory.createPolygon(outer, inner)
    }

    factory.createMultiPolygon(polygons.toArray)
  }

  private def getCoordinates(
      nodeIds: Seq[Long],
      subNodes: Map[Long, Node],
  ): Seq[Coordinate] = nodeIds.map { nodeId =>
    val node = subNodes(nodeId)
    new Coordinate(node.longitude, node.latitude)
  }

  private def mergeWays(ways: Seq[Seq[Coordinate]]): Seq[LinearRing] = {
    val grouped = ways
      .flatMap { way => Seq(way.head -> way, way.last -> way) }
      .groupMap(_._1)(_._2)

    val used = mutable.HashSet.empty[Seq[Coordinate]]

    val result = Seq.newBuilder[LinearRing]

    def nextConnectable(current: Seq[Coordinate]): Option[Seq[Coordinate]] = {
      val candidates =
        grouped.getOrElse(current.head, Seq.empty) ++
          grouped.getOrElse(current.last, Seq.empty)

      candidates.find { candidate =>
        !used(candidate) && canConnect(current, candidate)
      }
    }

    ways.foreach { way =>
      if !used(way) then {
        var current = way
        used += way

        var changed = true
        while changed && current.head != current.last do {
          nextConnectable(current) match {
            case Some(candidate) =>
              current = connectWays(current, candidate)

              used += candidate

            case None =>
              changed = false
          }
        }

        result += factory.createLinearRing(current.toArray)
      }
    }

    result.result
  }

  private def canConnect(a: Seq[Coordinate], b: Seq[Coordinate]): Boolean =
    a.nonEmpty && b.nonEmpty && (
      a.last == b.head ||
        a.last == b.last ||
        a.head == b.last ||
        a.head == b.head
    )

  private def connectWays(
      a: Seq[Coordinate],
      b: Seq[Coordinate],
  ): Seq[Coordinate] =
    if a.last == b.head then {
      a ++ b.tail
    } else if a.last == b.last then {
      a ++ b.reverse.tail
    } else if a.head == b.last then {
      b ++ a.tail
    } else if a.head == b.head then {
      b.reverse ++ a.tail
    } else {
      throw OsmDataException(s"Ways are not connectable: $a and $b")
    }

  extension (multiPolygon: MultiPolygon) {
    def calcArea: Double = {
      var totalArea = 0.0
      var polygonIndex = 0

      while polygonIndex < multiPolygon.getNumGeometries do {
        val polygon =
          multiPolygon.getGeometryN(polygonIndex).asInstanceOf[Polygon]

        // add up all outer rings
        totalArea += math.abs(projectedRingArea(polygon.getExteriorRing))

        // remove inner rings
        var holeIndex = 0
        while holeIndex < polygon.getNumInteriorRing do {
          totalArea -= math.abs(
            projectedRingArea(polygon.getInteriorRingN(holeIndex))
          )
          holeIndex += 1
        }

        polygonIndex += 1
      }

      totalArea
    }

    private def projectedRingArea(ring: LineString): Double = {
      val seq = ring.getCoordinateSequence
      val size = seq.size()

      if size < 4 then {
        0.0
      } else {
        val reusableCoordinate = new Coordinate()

        reusableCoordinate.x = seq.getX(0)
        reusableCoordinate.y = seq.getY(0)

        val firstProjected =
          GeoUtils.equalAreaProjection(reusableCoordinate)

        val firstX = firstProjected.getX
        val firstY = firstProjected.getY

        var previousX = firstX
        var previousY = firstY

        var sum = 0.0
        var i = 1

        while i < size do
          reusableCoordinate.x = seq.getX(i)
          reusableCoordinate.y = seq.getY(i)

          val projected =
            GeoUtils.equalAreaProjection(reusableCoordinate)

          val x = projected.getX
          val y = projected.getY

          sum += previousX * y - x * previousY

          previousX = x
          previousY = y

          i += 1

        if previousX != firstX || previousY != firstY then
          sum += previousX * firstY - firstX * previousY

        sum * 0.5
      }
    }
  }

  override protected def cleanUp(): Unit = {
    /* Nothing to do here. At least until now. */
  }
}
