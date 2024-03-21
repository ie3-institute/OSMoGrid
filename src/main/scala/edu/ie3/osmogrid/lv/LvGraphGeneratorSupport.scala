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
import edu.ie3.util.geo.GeoUtils.{buildCoordinate, orthogonalProjection}
import edu.ie3.util.geo.RichGeometries.{RichCoordinate, RichPolygon}
import edu.ie3.util.osm.model.OsmEntity.Way.ClosedWay
import edu.ie3.util.osm.model.OsmEntity.{Node, Way}
import edu.ie3.util.quantities.interfaces.Irradiance
import org.jgrapht.alg.connectivity.ConnectivityInspector
import org.locationtech.jts.geom.Coordinate
import tech.units.indriya.ComparableQuantity
import utils.OsmoGridUtils.{
  buildStreetGraph,
  calcHouseholdPower,
  isInsideLanduse,
  safeBuildPolygon
}

import java.util.UUID
import javax.measure.quantity.{Length, Power}
import scala.collection.parallel.ParSeq
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.util.{Success, Try}

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
    * @param isSubstation
    *   whether the building is a substation
    * @param buildingConnectionNode
    *   the node that connects the building to the grid
    */
  final case class BuildingGraphConnection(
      building: ClosedWay,
      center: Coordinate,
      buildingPower: ComparableQuantity[Power],
      highwayNodeA: Node,
      highwayNodeB: Node,
      graphConnectionNode: Node,
      isSubstation: Boolean,
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
          "Highway node between: " + highwayNodeA.id + " and " + highwayNodeB.id
        else if (this.graphConnectionNode == this.highwayNodeA)
          "Highway node: " + highwayNodeA.id
        else "Highway node: " + highwayNodeB.id
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
  def buildConnectedGridGraphs(
      osmoGridModel: LvOsmoGridModel,
      powerDensity: ComparableQuantity[Irradiance],
      minDistance: ComparableQuantity[Length],
      considerBuildingConnections: Boolean
  ): Seq[(OsmGraph, Seq[BuildingGraphConnection])] = {
    val (highways, highwayNodes) =
      OsmoGridModel.filterForWays(osmoGridModel.highways)
    val (building, buildingNodes) =
      OsmoGridModel.filterForClosedWays(osmoGridModel.buildings)
    val (landuses, landUseNodes) =
      OsmoGridModel.filterForClosedWays(osmoGridModel.landuses)
    val (substations, substationNodes) =
      OsmoGridModel.filterForClosedWays(osmoGridModel.existingSubstations)
    val buildingGraphConnections = calcBuildingGraphConnections(
      landuses,
      building,
      substations,
      highways,
      highwayNodes ++ buildingNodes ++ landUseNodes ++ substationNodes,
      powerDensity,
      minDistance
    )
    val streetGraph = buildStreetGraph(highways.seq.toSeq, highwayNodes)
    val (updatedGraph, updatedBgcs) =
      updateGraphWithBuildingConnections(
        streetGraph,
        buildingGraphConnections,
        considerBuildingConnections
      )

    divideDisconnectedGraphs(
      updatedGraph,
      updatedBgcs
    )
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
      substations: ParSeq[ClosedWay],
      highways: ParSeq[Way],
      nodes: Map[Long, Node],
      powerDensity: ComparableQuantity[Irradiance],
      minDistance: ComparableQuantity[Length]
  ): Seq[BuildingGraphConnection] = {
    val landusePolygons =
      landuses.map(closedWay => safeBuildPolygon(closedWay, nodes))
    (buildings ++ substations).flatMap(building => {
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
            closestOverall._2,
            substations.toSet.contains(building)
          )
        )
      } else None
    })
  }.seq.toSeq

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
        tags = Map.empty,
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
    * the edges of the surrounding nodes.
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
      buildingGraphConnections: Seq[BuildingGraphConnection],
      considerBuildingConnections: Boolean
  ): (OsmGraph, Seq[BuildingGraphConnection]) = {
    val updatedBgcs = buildingGraphConnections.map(bgc => {
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
          Map.empty,
          None
        )
        graph.addVertex(buildingNode)
        graph.addWeightedEdge(bgc.graphConnectionNode, buildingNode)
        bgc.copy(buildingConnectionNode = Some(buildingNode))
      } else bgc
    })
    (graph, updatedBgcs)
  }

  private def divideDisconnectedGraphs(
      graph: OsmGraph,
      buildingGraphConnections: Seq[
        LvGraphGeneratorSupport.BuildingGraphConnection
      ]
  ): Seq[(OsmGraph, Seq[BuildingGraphConnection])] = {
    val bgcMap =
      buildingGraphConnections.map(bgc => bgc.graphConnectionNode -> bgc).toMap

    val connectivityInspector = new ConnectivityInspector(graph)
    val connectedSets = connectivityInspector.connectedSets().asScala

    if (connectedSets.isEmpty) {
      throw OsmDataException(
        "Graph is empty, or no components could be determined."
      )
    } else if (connectedSets.size > 1) {
      connectedSets.foldLeft(
        Seq.empty[(OsmGraph, Seq[BuildingGraphConnection])]
      )((graphSeq, connectedSet) => {
        val subgraph = new OsmGraph()
        connectedSet.forEach(node => subgraph.addVertex(node))
        connectedSet.forEach { vertex =>
          val edges = graph.edgesOf(vertex).asScala
          edges.foreach { edge =>
            val source = graph.getEdgeSource(edge)
            val target = graph.getEdgeTarget(edge)
            if (
              connectedSet.contains(source) && connectedSet.contains(target)
            ) {
              subgraph.addEdge(source, target)
            }
          }
        }
        val connectivityInspector = new ConnectivityInspector(subgraph)
        if (!connectivityInspector.isConnected) {
          throw OsmDataException("Component is not connected")
        }
        val buildingGraphConnections =
          subgraph.vertexSet().asScala.flatMap(node => bgcMap.get(node)).toSeq
        graphSeq :+ (subgraph, buildingGraphConnections)
      })

    } else Seq((graph, buildingGraphConnections))
  }
}
