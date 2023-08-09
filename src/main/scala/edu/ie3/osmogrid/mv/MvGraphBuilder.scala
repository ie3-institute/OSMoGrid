/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.mv

import edu.ie3.datamodel.graph.DistanceWeightedEdge
import edu.ie3.datamodel.models.input.NodeInput
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.osmogrid.model.OsmoGridModel
import edu.ie3.osmogrid.model.OsmoGridModel.MvOsmoGridModel
import edu.ie3.osmogrid.mv.Savings.savingsAlgorithm
import edu.ie3.util.geo.GeoUtils
import edu.ie3.util.osm.model.OsmEntity.{Node, Way}
import org.jgrapht.GraphPath
import org.jgrapht.alg.interfaces.ShortestPathAlgorithm.SingleSourcePaths
import org.jgrapht.alg.shortestpath.BFSShortestPath
import tech.units.indriya.ComparableQuantity
import tech.units.indriya.quantity.Quantities
import tech.units.indriya.unit.Units

import javax.measure.quantity.Length

object MvGraphBuilder {
  final case class NodeConversion(
      conversionToOsm: Map[NodeInput, Node],
      conversionToPSDM: Map[Node, NodeInput]
  ) {
    def getOsmNode(node: NodeInput): Node = {
      conversionToOsm(node)
    }

    def getOsmNodes(nodes: List[NodeInput]): List[Node] = {
      nodes.map { node => conversionToOsm(node) }
    }

    def getPSDMNode(node: Node): NodeInput = {
      conversionToPSDM(node)
    }

    def getPSDMNodes(nodes: List[Node]): List[NodeInput] = {
      nodes.map { node => conversionToPSDM(node) }
    }
  }

  final case class MvConnection(
      nodeA: Node,
      nodeB: Node,
      distance: ComparableQuantity[Length],
      path: Option[GraphPath[Node, DistanceWeightedEdge]]
  )

  final case class MvGraph(
      nodeToHv: Node,
      osmNodes: List[Node],
      connections: List[MvConnection],
      nodeConversion: NodeConversion,
      graph: OsmGraph
  )

  def buildGraph(
      nodeToHv: NodeInput,
      nodesToLv: List[NodeInput],
      osmoGridModel: MvOsmoGridModel
  ): MvGraph = {
    val (highways, highwayNodes) =
      OsmoGridModel.filterForWays(osmoGridModel.highways)
    val nodes = nodesToLv.appended(nodeToHv)

    // building all necessary data for savings algorithm
    val conversion: NodeConversion = findClosestOsmNodes(nodes, highwayNodes)
    val osmNodesToLv: List[Node] = conversion.getOsmNodes(nodesToLv)
    val connections: List[MvConnection] = findClosestConnections(osmNodesToLv)

    // using savings algorithm to generate a graph structure
    val graph = savingsAlgorithm(
      conversion.getOsmNode(nodeToHv),
      osmNodesToLv,
      connections,
      conversion
    )

    // optional optimization step
    graphOptimizer(graph)
  }

  /** Method to create [[NodeConversion]].
    * @param nodes
    *   for which the conversion should be created
    * @param osmNodes
    *   map containing osm nodes
    * @return
    *   the [[NodeConversion]]
    */
  private def findClosestOsmNodes(
      nodes: List[NodeInput],
      osmNodes: Map[Long, Node]
  ): NodeConversion = {

    val conversion: Map[NodeInput, Node] = nodes.map { node =>
      val coordinate = node.getGeoPosition.getCoordinate

      val sortedList = osmNodes.values.toList
        .map { node: Node =>
          (
            node,
            GeoUtils.calcHaversine(coordinate, node.coordinate.getCoordinate)
          )
        }
        .sortBy(_._2)

      node -> sortedList(1)._1
    }.toMap

    NodeConversion(conversion, conversion.map { case (k, v) => v -> k })
  }

  // should return the closest connections for all nodes in this voronoi polynomial
  // the MvConnections can be used to build a mv graph
  private def findClosestConnections(
      osmNodes: List[Node]
  ): List[MvConnection] = {
    val connections: List[(Node, Node)] = getAllUniqueConnections(osmNodes)

    /*
      TODO: Change closest connection calculation after alternative is properly tested
      val streetGraph: OsmGraph = MvGraphBuilder.buildStreetGraph(highways.seq.toSeq, highwayNodes)
      buildMvConnections(streetGraph, highwayNodes, connections)
     */

    // uses haversine formula to calculate the aerial distance between two OSM Nodes
    // TODO: Replace it with the other findAllConnections method for possibly higher accuracy and less optimisation later
    connections.map { case (nodeA, nodeB) =>
      val distance = GeoUtils.calcHaversine(
        nodeA.coordinate.getCoordinate,
        nodeB.coordinate.getCoordinate
      )
      MvConnection(nodeA, nodeB, distance, None)
    }
  }

  // builds a street graph
  def buildStreetGraph(ways: Seq[Way], nodes: Map[Long, Node]): OsmGraph = ???

  // maybe necessary, when previous algorithm is not perfect
  private def graphOptimizer(mvGraph: MvGraph): MvGraph = ???

  // uses the street graph to find all connections between two OSM Nodes
  // returns a list of MvConnections
  // TODO: Testing if the selected shortest path algorithm is the best for our usecase
  private def buildMvConnections(
      osmGraph: OsmGraph,
      osmNodes: Map[Long, Node],
      connections: List[(Node, Node)]
  ): List[MvConnection] = {
    val shortestPath: BFSShortestPath[Node, DistanceWeightedEdge] =
      new BFSShortestPath(osmGraph)

    val nodes = osmNodes.values.toList
    val connections: List[(Node, Node)] = getAllUniqueConnections(nodes)

    val paths: Map[Node, SingleSourcePaths[Node, DistanceWeightedEdge]] =
      nodes.map(node => (node, shortestPath.getPaths(node))).toMap

    connections.map { case (nodeA, nodeB) =>
      val shortestPath = paths(nodeA)
      val graphPath = shortestPath.getPath(nodeB)

      MvConnection(
        nodeA,
        nodeB,
        Quantities.getQuantity(graphPath.getWeight, Units.METRE),
        Some(graphPath)
      )
    }
  }

  // used to get all possible unique connections (a -> b == b -> a)
  private def getAllUniqueConnections(
      nodes: List[Node]
  ): List[(Node, Node)] = {
    val connections: List[(Node, Node)] = List.empty

    // algorithm to find all unique combinations
    nodes.foreach(nodeA => {
      nodes.foreach(nodeB => {
        // it makes no sense to connect a node to itself => nodeA and nodeB cannot be the same
        if (nodeA != nodeB) {
          // two combinations possible
          val t1 = (nodeA, nodeB)
          val t2 = (nodeB, nodeA)

          // if none of the combinations is already added, the first combination is added
          if (!connections.contains(t1) && !connections.contains(t2)) {
            connections :+ t1
          }
        }
      })
    })

    // returns all unique connections
    connections
  }
}
