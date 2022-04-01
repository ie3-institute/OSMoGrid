/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import akka.actor.typed.scaladsl.Behaviors
import akka.util.Collections
import edu.ie3.datamodel.graph.{DistanceWeightedEdge, DistanceWeightedGraph}
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.osmogrid.model.OsmoGridModel.EnhancedOsmEntity
import edu.ie3.util.geo.GeoUtils
import edu.ie3.util.geo.GeoUtils.buildCoordinate
import edu.ie3.util.osm.OsmUtils
import edu.ie3.util.osm.model.OsmEntity.Way
import edu.ie3.util.osm.model.OsmEntity.Way.{ClosedWay, OpenWay}
import edu.ie3.util.osm.model.OsmEntity.Node
import edu.ie3.util.quantities.interfaces.PowerDensity
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.math.{Vector2D, Vector3D}
import tech.units.indriya.ComparableQuantity

import javax.measure.quantity.Length
import scala.collection.immutable.{AbstractSeq, LinearSeq}
import scala.collection.parallel.{ParSeq, immutable}

object LvGridGenerator {
  sealed trait Request

  sealed trait Response
  final case class RepLvGrid(grid: SubGridContainer) extends Response

  final case class BuildingGraphConnection(
    building: ClosedWay,
    center: Coordinate,
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


  private def buildStreetGraph(enhancedEntities: Seq[EnhancedOsmEntity]): OsmGraph = {
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
//          val e = new DistanceWeightedEdge()
//          graph.setEdgeWeight(e, weight.getValue.doubleValue)
//          graph.addEdge(nodeA, nodeB, e) // TODO: consider checking boolean from this method
//        }})
//      graph
    ???
  }

  private def buildStreetGraph(ways: Seq[Way], nodes: Map[Long, Node]): OsmGraph = {
    val graph = new OsmGraph()
    ways.foreach(way => {
      val nodeIds = way.nodes
      nodeIds.sliding(2).foreach {
        case Seq(nodeAId, nodeBId) =>
          val nodeA = nodes.getOrElse(nodeAId, throw IllegalArgumentException(s"Node $nodeAId of Way ${way.id} is not within our nodes mapping"))
          val nodeB = nodes.getOrElse(nodeBId, throw IllegalArgumentException(s"Node $nodeBId of Way ${way.id} is not within our nodes mapping"))
          graph.addVertex(nodeA)
          graph.addVertex(nodeB)
          // calculate edge weight
          val weight = GeoUtils.calcHaversine(nodeA.latitude, nodeA.longitude, nodeB.latitude, nodeB.longitude)
          // create edge and add edge to rawGraph
          val e = new DistanceWeightedEdge()
          graph.setEdgeWeight(e, weight.getValue.doubleValue)
          graph.addEdge(nodeA, nodeB, e) // TODO: consider checking boolean from this method
      }
  })
    graph
  }

  private def calcPerpendicularDistanceMatrix(buildings: Seq[EnhancedOsmEntity], comparableQuantity: ComparableQuantity[PowerDensity]) = {
    ???
  }

  // todo: I probably can do this parallel to building the street graph
  private def calcBuildingGraphConnections(buildings: Seq[ClosedWay], highways: Seq[Way], nodes: Map[Long, Node], comparableQuantity: ComparableQuantity[PowerDensity]): Seq[BuildingGraphConnection] = {

    // for all buildings
    buildings.map(
      building => {
        // check if building is inside residential area
        if(OsmUtils.isInsideLandUse(building)) {
          // for all ways
          highways.foreach(highway => {
            highway.nodes.sliding(2).foreach{
              // for every two nodes of way
              case Seq(nodeAId, nodeBId) =>
              // calculate orthogonal projection
                val nodeA = nodes.getOrElse(nodeAId, throw IllegalArgumentException(s"Node $nodeAId of Way ${highway.id} is not within our nodes mapping"))
                val coordinateA = buildCoordinate(nodeA.latitude, nodeA.longitude)
                val nodeB = nodes.getOrElse(nodeBId, throw IllegalArgumentException(s"Node $nodeBId of Way ${highway.id} is not within our nodes mapping"))
                val coordinateB = buildCoordinate(nodeB.latitude, nodeB.longitude)
                val orthogonalProjection = orthogonalProjection(coordinateA, coordinateB)
                // if orthogonal point is on the line
                if(orthogonalProjection.isBetween(coordinateA, coordinateB)){

                }
                // take it since this is the closest we will get
            }
          })
          // todo: What if this point is not actually between the points of the line

        // todo: what is the minimum distance to not create a new point but use an existing one?

        // if orthogonal point is not on the line
        // take smallest of start and end of line

        // calculate load of house

        // return Seq[(House, BuildingGraphConnection)]

        }
      }
    )
  }


  // todo: Knoten die nur Knickpunkte sind werden rausgefiltert -> LF Berechnung -> Knicks als Geoposition

  private def getClosest() = {
    ???
  }


  private def orthogonalProjection(linePtA: Coordinate, linePtB: Coordinate, pt: Coordinate): Coordinate = {
    orthogonalProjection(Vector2D.create(linePtA), Vector2D.create(linePtB), Vector2D.create(pt)).toCoordinate
  }

  /** Calculate the orthogonal projection of a point onto a line
    * Check out how and why this https://stackoverflow.com/questions/54009832/scala-orthogonal-projection-of-a-point-onto-a-line
    *
    * @param linePtA first point of the line
    * @param linePtB second point of the line
    * @param pt the point for which to calculate the projection
    * @return the projected point
    */
  private def orthogonalProjection(pt: Vector2D, linePtA: Vector2D, linePtB: Vector2D): Vector2D = {
    val v = linePtA.subtract(pt)
    val d = linePtB.subtract(linePtA)
    linePtA.add(d.multiply((v dot d) / d.lengthSquared()))
  }

}
