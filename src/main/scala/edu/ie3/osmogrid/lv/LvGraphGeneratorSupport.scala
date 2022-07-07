/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import edu.ie3.osmogrid.exception.OsmDataException
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.osmogrid.model.OsmoGridModel
import edu.ie3.osmogrid.model.OsmoGridModel.LvOsmoGridModel
import edu.ie3.util.geo.GeoUtils.buildCoordinate
import edu.ie3.util.osm.model.OsmEntity.{Node, Way}
import edu.ie3.util.osm.model.OsmEntity.Way.ClosedWay
import edu.ie3.util.quantities.interfaces.Irradiance
import org.locationtech.jts.geom.Coordinate
import tech.units.indriya.ComparableQuantity
import java.util.UUID
import javax.measure.quantity.{Length, Power}
import scala.collection.parallel.ParSeq
import scala.util.{Success, Try}
import edu.ie3.util.geo.RichGeometries.{RichCoordinate, RichPolygon}
import utils.OsmogridUtils.{
  calcHouseholdPower,
  isInsideLanduse,
  orthogonalProjection,
  safeBuildPolygon
}

object LvGraphGeneratorSupport {

  /** Resembles a calculated connection between a building and a grid. Every
    * building gets connected on the nearest point at the nearest highway
    * section. The connection point can either be a new node on mentioned
    * highway section or one of the two highway section nodes
    *
    * @param building
    *   the building to connect
    * @param center
    *   the building center
    * @param buildingPower
    *   the building power
    * @param highwayNodeA
    *   node a of the highway section at which the building gets connected
    * @param highwayNodeB
    *   node b of the highway section at which the building gets connected
    * @param graphConnectionNode
    *   the graph connection node
    */
  final case class BuildingGraphConnection(
      building: ClosedWay,
      center: Coordinate,
      buildingPower: ComparableQuantity[Power],
      highwayNodeA: Node,
      highwayNodeB: Node,
      graphConnectionNode: Node,
      buildingConnectionNode: Option[Node] = None
  ) {

    /** Checks whether the graph connection node is a new node. If not it is one
      * of the highway sections
      *
      * @return
      *   whether the graph connection is a new node
      */
    def hasNewNode: Boolean = {
      (graphConnectionNode != highwayNodeA) && (graphConnectionNode != highwayNodeB)
    }

    def createHighwayNodeName(considerHouseConnectionNode: Boolean): String = {
      if (considerHouseConnectionNode) {
        if (this.hasNewNode)
          "Node highway between: " + highwayNodeA.id + " and " + highwayNodeB.id
        else if (this.graphConnectionNode == this.highwayNodeA)
          "Node highway: " + highwayNodeA.id
        else "Node highway: " + highwayNodeB.id
      } else "Building connection: " + this.building.id
    }

    def createBuildingNodeName(): String = {
      "Building connection: " + this.building.id
    }
  }

  /** Builds an extended weighted street graph that resembles not only the
    * streets but also the building graph connections.
    *
    * @param osmoGridModel
    *   the osmo grid data for which to build the graph
    * @param powerDensity
    *   the power density of a household
    * @param minDistance
    *   the minimum distance above which to build new nodes on the street graph
    *   for building connections
    * @return
    */
  def buildGridGraph(
      osmoGridModel: LvOsmoGridModel,
      powerDensity: ComparableQuantity[Irradiance],
      minDistance: ComparableQuantity[Length],
      considerBuildingConnections: Boolean
  ): (OsmGraph, ParSeq[BuildingGraphConnection]) = {
    val (highways, highwayNodes) =
      OsmoGridModel.filterForWays(osmoGridModel.highways)
    val (building, buildingNodes) =
      OsmoGridModel.filterForClosedWays(osmoGridModel.buildings)
    val (landuses, landUseNodes) =
      OsmoGridModel.filterForClosedWays(osmoGridModel.landuses)
    val buildingGraphConnections = calcBuildingGraphConnections(
      landuses,
      building,
      highways,
      highwayNodes ++ buildingNodes ++ landUseNodes,
      powerDensity,
      minDistance
    )
    val streetGraph = buildStreetGraph(highways.seq.toSeq, highwayNodes)
    (
      updateGraphWithBuildingConnections(
        streetGraph,
        buildingGraphConnections,
        considerBuildingConnections
      ),
      buildingGraphConnections
    )
  }

  /** builds a weighted street graph out ways and nodes.
    *
    * @param ways
    *   the ways
    * @param nodes
    *   the nodes
    * @return
    *   the street graph
    */
  private def buildStreetGraph(
      ways: Seq[Way],
      nodes: Map[Long, Node]
  ): OsmGraph = {
    val graph = new OsmGraph()
    ways.foreach(way => {
      val nodeIds = way.nodes
      nodeIds.sliding(2).foreach { case Seq(nodeAId, nodeBId) =>
        (nodes.get(nodeAId), nodes.get(nodeBId)) match {
          case (Some(nodeA), Some(nodeB)) =>
            graph.addVertex(nodeA)
            graph.addVertex(nodeB)
            graph.addWeightedEdge(nodeA, nodeB)

          case (None, _) =>
            throw new IllegalArgumentException(
              s"Node $nodeAId of Way ${way.id} is not within our nodes mapping"
            )
          case (_, None) =>
            throw new IllegalArgumentException(
              s"Node $nodeBId of Way ${way.id} is not within our nodes mapping"
            )
        }
      }
    })
    graph
  }

  /** Calculates building graph connections of buildings to the nearest highway
    * sections
    *
    * @param landuses
    *   all landuses
    * @param buildings
    *   all buildings
    * @param highways
    *   all highways
    * @param nodes
    *   all nodes
    * @param powerDensity
    *   the average power density of a house
    * @param minDistance
    *   the minimum distance above which to build new nodes on the street graph
    *   for building connections
    * @return
    *   all building graph connections
    */
  private def calcBuildingGraphConnections(
      landuses: ParSeq[ClosedWay],
      buildings: ParSeq[ClosedWay],
      highways: ParSeq[Way],
      nodes: Map[Long, Node],
      powerDensity: ComparableQuantity[Irradiance],
      minDistance: ComparableQuantity[Length]
  ): ParSeq[BuildingGraphConnection] = {
    val landusePolygons =
      landuses.map(closedWay => safeBuildPolygon(closedWay, nodes))
    buildings.flatMap(building => {
      val buildingPolygon = safeBuildPolygon(building, nodes)
      val buildingCenter: Coordinate = buildingPolygon.getCentroid.getCoordinate
      // check if building is inside residential area
      if (isInsideLanduse(buildingCenter, landusePolygons)) {

        val closest = highways.flatMap(highway => {
          // get closest to each highway section
          highway.nodes.sliding(2).map { case Seq(nodeAId, nodeBId) =>
            (nodes.get(nodeAId), nodes.get(nodeBId)) match {
              case (None, _) =>
                throw new IllegalArgumentException(
                  s"Node $nodeAId is not within our nodes mapping"
                )
              case (_, None) =>
                throw new IllegalArgumentException(
                  s"Node $nodeBId is not within our nodes mapping"
                )
              case (Some(nodeA), Some(nodeB)) =>
                val (distance, node) = getClosest(
                  nodeA,
                  nodeB,
                  buildingCenter,
                  minDistance
                ).getOrElse(
                  throw OsmDataException(
                    s"Could not retrieve closest nodes for highway ${highway.id}"
                  )
                )
                (distance, node, nodeA, nodeB)
            }
          }
        })

        val closestOverall = closest minBy {
          _._1
        }
        // calculate load of house
        val load =
          calcHouseholdPower(buildingPolygon.calcAreaOnEarth, powerDensity)
        Some(
          BuildingGraphConnection(
            building,
            buildingCenter,
            load,
            closestOverall._3,
            closestOverall._4,
            closestOverall._2
          )
        )
      } else None
    })
  }

  /** Get closest point of the buildings center to the highway section spanning
    * linePtA and linePtB. If we find a point closer to the building center that
    * is not linePtA nor linePtB we only take it if it is sufficiently far away
    * (further than minDistance) otherwise we go with line point nearby to not
    * inflate the number of nodes.
    *
    * @param buildingCenter
    *   center coordinate of the building
    * @param wayNodeA
    *   node a of the way section
    * @param wayNodeB
    *   node b of the way section
    * @param minDistance
    *   the minimum distance above which to build new nodes on the street graph
    *   for building connections
    * @return
    *   a Tuple of the distance and the point
    */
  private def getClosest(
      wayNodeA: Node,
      wayNodeB: Node,
      buildingCenter: Coordinate,
      minDistance: ComparableQuantity[Length]
  ): Try[(ComparableQuantity[Length], Node)] = {
    val coordinateA = buildCoordinate(wayNodeA.latitude, wayNodeA.longitude)
    val coordinateB = buildCoordinate(wayNodeB.latitude, wayNodeB.longitude)
    val orthogonalPt =
      orthogonalProjection(coordinateA, coordinateB, buildingCenter)
    // if orthogonal point is on the line and far enough apart from the line points
    // take it since this is the closest we will get
    if (
      orthogonalPt.isBetween(
        coordinateA,
        coordinateB,
        1e-3
      ) && ((orthogonalPt haversineDistance coordinateA) isGreaterThan minDistance)
      && ((orthogonalPt haversineDistance coordinateB) isGreaterThan minDistance)
    ) {
      val closestNode = Node(
        id = UUID.randomUUID().getMostSignificantBits,
        latitude = orthogonalPt.y,
        longitude = orthogonalPt.x,
        tags = Map(),
        metaInformation = None
      )
      Success(buildingCenter.haversineDistance(orthogonalPt), closestNode)
    }
    // take the nearer point of the two line points
    else {
      val coordinateADistance = buildingCenter.haversineDistance(coordinateA)
      val coordinateBDistance = buildingCenter.haversineDistance(coordinateB)
      if (coordinateADistance.isLessThan(coordinateBDistance))
        Success((coordinateADistance, wayNodeA))
      else Success((coordinateBDistance, wayNodeB))
    }
  }

  /** Updates the graph by adding the building graph connections and updating
    * the edges of the surrounding nodes
    *
    * @param graph
    *   the graph to update
    * @param buildingGraphConnections
    *   the building-graph connections to update with
    * @return
    *   the updated OsmGraph
    */
  private def updateGraphWithBuildingConnections(
      graph: OsmGraph,
      buildingGraphConnections: ParSeq[BuildingGraphConnection],
      considerBuildingConnections: Boolean
  ): OsmGraph = {
    buildingGraphConnections.foreach(bgc => {
      if (bgc.hasNewNode) {
        graph.addVertex(bgc.graphConnectionNode)
        graph.removeEdge(bgc.highwayNodeA, bgc.highwayNodeB)
        graph.addWeightedEdge(bgc.highwayNodeA, bgc.graphConnectionNode)
        graph.addWeightedEdge(bgc.graphConnectionNode, bgc.highwayNodeB)
      }
      if (considerBuildingConnections) {
        val buildingNode = Node(
          bgc.building.id,
          bgc.center.y,
          bgc.center.x,
          Map().empty,
          None
        )
        graph.addVertex(buildingNode)
        graph.addWeightedEdge(bgc.graphConnectionNode, buildingNode)
        bgc.copy(buildingConnectionNode = Some(buildingNode))
      }
    })
    graph
  }

}
