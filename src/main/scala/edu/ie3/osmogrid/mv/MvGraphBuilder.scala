/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.mv

import edu.ie3.datamodel.graph.DistanceWeightedEdge
import edu.ie3.datamodel.models.input.NodeInput
import edu.ie3.osmogrid.graph.OsmGraph
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
      node: NodeInput,
      osmNode: Node
  )
  final case class MvConnections(
      nodeA: Node,
      nodeB: Node,
      distance: ComparableQuantity[Length],
      path: Option[GraphPath[Node, DistanceWeightedEdge]]
  )

  /** Method to find the closest [[Node]] for a given [[NodeInput]].
    * @param node
    *   for which the closest osm node should be returned
    * @param osmNodes
    *   map containing osm nodes
    * @return
    *   the closest [[Node]]
    */
  def findClosestOsmNode(
      node: NodeInput,
      osmNodes: Map[Long, Node]
  ): NodeConversion = {
    val coordinate = node.getGeoPosition.getCoordinate

    val sortedList = osmNodes.values.toList
      .map { node: Node =>
        (
          node,
          GeoUtils.calcHaversine(coordinate, node.coordinate.getCoordinate)
        )
      }
      .sortBy(_._2)

    NodeConversion(node, sortedList(1)._1)
  }

  // uses haversine formula to calculate the aerial distance between two OSM Nodes
  // TODO: Replace it with the other findAllConnections method for possibly higher accuracy and less optimisation later
  def findAllConnections(osmNodes: Map[Long, Node]): List[MvConnections] = {
    val nodes: List[Node] = osmNodes.values.toList
    val connections: List[(Node, Node)] = getAllUniqueConnections(nodes)

    connections.map { case (nodeA, nodeB) =>
      val distance = GeoUtils.calcHaversine(
        nodeA.coordinate.getCoordinate,
        nodeB.coordinate.getCoordinate
      )
      MvConnections(nodeA, nodeB, distance, None)
    }
  }

  // uses the street graph to find all connections between two OSM Nodes
  // returns a list of MvConnections
  // TODO: Testing if the selected shortest path algorithm is the best for our usecase
  def findAllConnections(
      osmGraph: OsmGraph,
      osmNodes: Map[Long, Node]
  ): List[MvConnections] = {
    val shortestPath: BFSShortestPath[Node, DistanceWeightedEdge] =
      new BFSShortestPath(osmGraph)

    val nodes = osmNodes.values.toList
    val connections: List[(Node, Node)] = getAllUniqueConnections(nodes)

    val paths: Map[Node, SingleSourcePaths[Node, DistanceWeightedEdge]] =
      nodes.map(node => (node, shortestPath.getPaths(node))).toMap

    connections.map { case (nodeA, nodeB) =>
      val shortestPath = paths(nodeA)
      val graphPath = shortestPath.getPath(nodeB)

      MvConnections(
        nodeA,
        nodeB,
        Quantities.getQuantity(graphPath.getWeight, Units.METRE),
        Some(graphPath)
      )
    }
  }

  // builds a street graph
  def buildStreetGraph(ways: Seq[Way], nodes: Map[Long, Node]): OsmGraph = ???

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
