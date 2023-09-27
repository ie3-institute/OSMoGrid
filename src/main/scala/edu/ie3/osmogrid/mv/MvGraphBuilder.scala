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
import edu.ie3.osmogrid.routingproblem.Solver.solve
import edu.ie3.util.geo.GeoUtils
import edu.ie3.util.osm.model.OsmEntity.{Node, Way}
import org.jgrapht.alg.interfaces.ShortestPathAlgorithm.SingleSourcePaths
import org.jgrapht.alg.shortestpath.BFSShortestPath
import tech.units.indriya.quantity.Quantities
import tech.units.indriya.unit.Units
import utils.MvUtils.{
  Connection,
  Connections,
  NodeConversion,
  getAllUniqueCombinations
}

object MvGraphBuilder {

  final case class MvGraph(
      nodeToHv: Node,
      osmNodes: List[Node],
      connections: List[Connection],
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
    val connections: Connections = findClosestConnections(
      conversion.getOsmNodes(nodes)
    )

    // using savings algorithm to generate a graph structure
    val graph = solve(
      conversion.getOsmNode(nodeToHv),
      connections
    )

    // optional optimization step
    // graphOptimizer(graph)

    ???
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
  ): Connections = {
    val possibleConnections: List[(Node, Node)] =
      getAllUniqueCombinations(
        osmNodes
      )

    /*
      TODO: Change closest connection calculation after alternative is properly tested
      val streetGraph: OsmGraph = MvGraphBuilder.buildStreetGraph(highways.seq.toSeq, highwayNodes)
      buildMvConnections(streetGraph, highwayNodes, connections)
     */

    // uses haversine formula to calculate the aerial distance between two OSM Nodes
    // TODO: Replace it with the other findAllConnections method for possibly higher accuracy and less optimisation later
    val connections = possibleConnections.map { case (nodeA, nodeB) =>
      val distance = GeoUtils.calcHaversine(
        nodeA.coordinate.getCoordinate,
        nodeB.coordinate.getCoordinate
      )
      Connection(nodeA, nodeB, distance, None)
    }

    Connections(osmNodes, connections)
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
  ): List[Connection] = {
    val shortestPath: BFSShortestPath[Node, DistanceWeightedEdge] =
      new BFSShortestPath(osmGraph)

    val nodes = osmNodes.values.toList
    val connections: List[(Node, Node)] = getAllUniqueCombinations(nodes)

    val paths: Map[Node, SingleSourcePaths[Node, DistanceWeightedEdge]] =
      nodes.map(node => (node, shortestPath.getPaths(node))).toMap

    connections.map { case (nodeA, nodeB) =>
      val shortestPath = paths(nodeA)
      val graphPath = shortestPath.getPath(nodeB)

      Connection(
        nodeA,
        nodeB,
        Quantities.getQuantity(graphPath.getWeight, Units.METRE),
        Some(graphPath)
      )
    }
  }
}
