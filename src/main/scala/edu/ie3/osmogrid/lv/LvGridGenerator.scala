/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import akka.actor.typed.scaladsl.Behaviors
import akka.util.Collections
import edu.ie3.datamodel.graph.{DistanceWeightedEdge, DistanceWeightedGraph}
import edu.ie3.datamodel.models.StandardUnits
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.osmogrid.exception.MissingOsmDataException
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.osmogrid.model.OsmoGridModel.EnhancedOsmEntity
import edu.ie3.util.geo.{GeoUtils, RichGeometries}
import edu.ie3.util.geo.GeoUtils.{buildCoordinate, calcHaversine}
import edu.ie3.util.osm.OsmUtils
import edu.ie3.util.osm.model.OsmEntity.Way
import edu.ie3.util.osm.model.OsmEntity.Way.{ClosedWay, OpenWay}
import edu.ie3.util.osm.model.OsmEntity.Node
import edu.ie3.util.quantities.interfaces.{Irradiance, PowerDensity}
import org.locationtech.jts.geom.{Coordinate, Polygon}
import org.locationtech.jts.math.{Vector2D, Vector3D}
import tech.units.indriya.ComparableQuantity
import edu.ie3.util.geo.RichGeometries.RichPolygon
import edu.ie3.util.geo.RichGeometries.RichCoordinate
import edu.ie3.util.osm.OsmUtils.GeometryUtils
import edu.ie3.util.osm.OsmUtils.GeometryUtils.buildPolygon
import edu.ie3.util.quantities.{PowerSystemUnits, QuantityUtil}
import tech.units.indriya.quantity.Quantities
import tech.units.indriya.unit.Units

import javax.measure.Unit
import java.util.UUID
import javax.measure.Quantity
import javax.measure.quantity.{Area, Length, Power}
import scala.collection.immutable.{AbstractSeq, LinearSeq}
import scala.collection.parallel.{ParSeq, immutable}
import scala.math.BigDecimal.RoundingMode
import scala.util.{Failure, Success, Try}

object LvGridGenerator {
  sealed trait Request

  sealed trait Response
  final case class RepLvGrid(grid: SubGridContainer) extends Response

  final case class BuildingGraphConnection(
      building: ClosedWay,
      center: Coordinate,
      buildingPower: ComparableQuantity[Power],
      connectedHighway: Way,
      nearestNode: Node
  )

  def apply(): Behaviors.Receive[Request] = idle

  private def idle: Behaviors.Receive[Request] = Behaviors.receive {
    case (ctx, unsupported) =>
      ctx.log.warn(s"Received unsupported message '$unsupported'.")
      Behaviors.stopped
  }

  // Process:
  // buildStreetGraph
  // calcBuildingGraphConnection
  // updateGraph
  // removeEmptyLandUses
  // cleanGraph (?) remove unneccessary dead ends
  // createClusters
  // createSubgraphs(clusters)

  private def buildStreetGraph(
      enhancedEntities: Seq[EnhancedOsmEntity]
  ): OsmGraph = {
//      val graph = new OsmGraph()
//      enhancedEntities.foreach(enhancedEntity => {
//        // todo: unsafe
//        val way = enhancedEntity.entity.asInstanceOf[Way]
//        val nodeIds = way.nodes
//        for (i <- 1 until nodeIds.size) {
//          // todo: unsafe
//          val nodeA = enhancedEntity.subEntities(nodeIds(i - 1)).asInstanceOf[Node]
//          val nodeB = enhancedEntity.subEntities(nodeIds(i)).asInstanceOf[Node]
//          graph.addVertex(nodeA)
//          graph.addVertex(nodeB)
//          // calculate edge weight
//          val weight = GeoUtils.calcHaversine(nodeA.latitude, nodeA.longitude, nodeB.latitude, nodeB.longitude)
//          // create edge and add edge to rawGraph
//          val edge = new DistanceWeightedEdge()
//          graph.setEdgeWeight(edge, weight.getValue.doubleValue)
//          graph.addEdge(nodeA, nodeB, edge) // TODO: consider checking boolean from this method
//        }})
//      graph
    ???
  }

  private def buildStreetGraph(
      ways: Seq[Way],
      nodes: Map[Long, Node]
  ): OsmGraph = {
    val graph = new OsmGraph()
    ways.foreach(way => {
      val nodeIds = way.nodes
      nodeIds.sliding(2).foreach { case Seq(nodeAId, nodeBId) =>
        val nodeA = nodes.getOrElse(
          nodeAId,
          throw IllegalArgumentException(
            s"Node $nodeAId of Way ${way.id} is not within our nodes mapping"
          )
        )
        val nodeB = nodes.getOrElse(
          nodeBId,
          throw IllegalArgumentException(
            s"Node $nodeBId of Way ${way.id} is not within our nodes mapping"
          )
        )
        graph.addVertex(nodeA)
        graph.addVertex(nodeB)
        val weight = GeoUtils.calcHaversine(
          nodeA.latitude,
          nodeA.longitude,
          nodeB.latitude,
          nodeB.longitude
        )
        // create edge and add edge to rawGraph
        val edge = new DistanceWeightedEdge()
        graph.setEdgeWeight(edge, weight.getValue.doubleValue)
        graph.addEdge(nodeA, nodeB, edge) // TODO: consider checking boolean from this method
      }
    })
    graph
  }

  private def calcPerpendicularDistanceMatrix(
      buildings: Seq[EnhancedOsmEntity],
      comparableQuantity: ComparableQuantity[PowerDensity]
  ) = {
    ???
  }

  // todo: I probably can do this parallel to building the street graph
  private def calcBuildingGraphConnections(
      landuses: Seq[ClosedWay],
      buildings: Seq[ClosedWay],
      highways: Seq[Way],
      nodes: Map[Long, Node],
      powerDensity: ComparableQuantity[Irradiance],
      minDistance: ComparableQuantity[Length]
  ): Seq[BuildingGraphConnection] = {
    val landusePolygons = landuses.map(landuse =>
      buildPolygon(landuse, nodes) match {
        case Failure(exc) =>
          throw MissingOsmDataException(
            s"Could not convert landuse with id: ${landuse.id} since node was not part of nodes mapping",
            exc
          )
        case Success(polygon) => polygon
      }
    )
    buildings.flatMap(building => {
      val buildingPolygon = buildPolygon(building, nodes) match {
        case Failure(exc) =>
          throw MissingOsmDataException(
            s"Could not convert building with id: ${building.id} since node was not part of nodes mapping",
            exc
          )
        case Success(polygon) => polygon
      }
      val buildingCenter: Coordinate = buildingPolygon.getCentroid.getCoordinate
      // check if building is inside residential area
      if (isInsideLanduse(buildingCenter, landusePolygons)) {
        val closestOverall = highways.map(highway => {
          highway.nodes.sliding(2).map {
            case Seq(nodeAId, nodeBId) =>
              val (distance, node) = getClosest(
                buildingCenter,
                nodeAId,
                nodeBId,
                nodes,
                minDistance
              ) match {
                case Failure(exc) =>
                  throw MissingOsmDataException(
                    s"Could not retrieve closest nodes for highway ${highway.id}",
                    exc
                  )
                case Success(closest) => closest
              }
              (distance, node, highway)
            // todo: it might be faster to go with a reduce operation
          } minBy {
            _._1
          }
        }) minBy {
          _._1
        }
        // calculate load of house
        val load = calcPower(buildingPolygon.calcAreaOnEarth, powerDensity)
        Some(BuildingGraphConnection(building, buildingCenter, load, closestOverall._3, closestOverall._2))
      }
      else None
    })
  }

  def isInsideLanduse(
    buildingCenter: Coordinate,
    landuses: Seq[Polygon]
  ): Boolean = {
    for (landuse <- landuses) {
      if (landuse.containsCoordinate(buildingCenter)) return true
    }
    false
  }

  // todo: Knoten die nur Knickpunkte sind werden rausgefiltert -> LF Berechnung -> Knicks als Geoposition

  /** Get closest point of the buildings center to the highway section spanning linePtA and linePtB. If we find
    * a point closer to the building center that is not linePtA nor linePtB we only take it if it is sufficiently
    * far away (further than minDistance) otherwise we go with line point nearby to not inflate the number of points.
    *
    * @param buildingCenter center coordinate of the building
    * @param linePtA point a of the way section
    * @param linePtB point b of the way section
    * @param nodes node id to node map
    * @param minDistance minimum distance for creating a new point
    * @return a Tuple of the distance and the point
    */
  private def getClosest(
      buildingCenter: Coordinate,
      linePtA: Long,
      linePtB: Long,
      nodes: Map[Long, Node],
      minDistance: ComparableQuantity[Length]
  ): Try[(ComparableQuantity[Length], Node)] =
    (nodes.get(linePtA), nodes.get(linePtB)) match {
      case (Some(nodeA), Some(nodeB)) =>
        val coordinateA = buildCoordinate(nodeA.latitude, nodeA.longitude)
        val coordinateB = buildCoordinate(nodeB.latitude, nodeB.longitude)
        val orthogonalPt =
          orthogonalProjection(buildingCenter, coordinateA, coordinateB)
        // if orthogonal point is on the line
        // take it since this is the closest we will get
        if (
          orthogonalPt.isBetween(
            coordinateA,
            coordinateB
          ) && ((orthogonalPt haversineDistance coordinateA) isGreaterThan minDistance)
          && ((orthogonalPt haversineDistance coordinateB) isGreaterThan minDistance)
        ) {
          Node(
            id = UUID.randomUUID().getMostSignificantBits,
            latitude = orthogonalPt.y,
            longitude = orthogonalPt.x,
            tags = Map(),
            metaInformation = None
          )
        }
        val coordinateADistance = buildingCenter.haversineDistance(coordinateA)
        val coordinateBDistance = buildingCenter.haversineDistance(coordinateB)
        if (coordinateADistance.isLessThan(coordinateBDistance))
          Success((coordinateADistance, nodeA))
        else Success((coordinateBDistance, nodeB))
      case (None, _) =>
        Failure(
          IllegalArgumentException(
            s"Node $linePtA is not within our nodes mapping"
          )
        )
      case (_, None) =>
        Failure(
          IllegalArgumentException(
            s"Node $linePtB is not within our nodes mapping"
          )
        )
    }

  private def orthogonalProjection(
      linePtA: Coordinate,
      linePtB: Coordinate,
      pt: Coordinate
  ): Coordinate = {
    orthogonalProjection(
      Vector2D.create(linePtA),
      Vector2D.create(linePtB),
      Vector2D.create(pt)
    ).toCoordinate
  }

  /** Calculate the orthogonal projection of a point onto a line Check out how
    * and why this
    * https://stackoverflow.com/questions/54009832/scala-orthogonal-projection-of-a-point-onto-a-line
    *
    * @param linePtA
    *   first point of the line
    * @param linePtB
    *   second point of the line
    * @param pt
    *   the point for which to calculate the projection
    * @return
    *   the projected point
    */
  private def orthogonalProjection(
      pt: Vector2D,
      linePtA: Vector2D,
      linePtB: Vector2D
  ): Vector2D = {
    val v = linePtA.subtract(pt)
    val d = linePtB.subtract(linePtA)
    linePtA.add(d.multiply((v dot d) / d.lengthSquared()))
  }

  /**
    * Calculates the power value of a household load based on the provided building area and the
    * provided average power density value and the provided average household area size
    *
    * @param area area of the household
    * @param powerDensity average power per area
    */
  private def calcPower(area: ComparableQuantity[Area], powerDensity: ComparableQuantity[Irradiance]): ComparableQuantity[Power] = {
    val power = area.to(Units.SQUARE_METRE)
      .multiply(powerDensity.to(PowerSystemUnits.WATT_PER_SQUAREMETRE))
      .asType(classOf[Power])
      .to(PowerSystemUnits.KILOWATT)
    round(power, 4)
  }

  // todo: move to PowerSystemUtils -> QuantityUtils
  private def round[T <: Quantity[T]](quantity: ComparableQuantity[T], decimals: Int): ComparableQuantity[T] = {
    if (decimals < 0) throw IllegalArgumentException("You can not round to negative decimal places.")
    val rounded = BigDecimal.valueOf(quantity.getValue.doubleValue()).setScale(decimals, RoundingMode.HALF_UP).doubleValue
    Quantities.getQuantity(rounded, quantity.getUnit)
  }

}
