/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.routingproblem

import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.osmogrid.mv.MvGraphBuilder.{MvGraph, NodeConversion}
import edu.ie3.osmogrid.routingproblem.Definitions.{
  Connection,
  Connections,
  Saving
}
import edu.ie3.util.osm.model.OsmEntity.Node

import javax.measure.Quantity
import javax.measure.quantity.Length

/** Solver for the routing problem. Uses a combination of savings algorithm,
  * tabu lists and nearest neighbour search.
  */
object Solver {

  // uses saving algorithm to minimize the connection length
  def savingsAlgorithm(
      nodeToHv: Node,
      connections: Connections,
      conversion: NodeConversion
  ): MvGraph = {
    val graph = firstStep(nodeToHv, connections)

    ???
  }

  def calcSavings(
      nodeToHv: Node,
      connections: List[Connection],
      graph: OsmGraph
  ): Seq[Saving] = {
    connections.map { connection =>
      calcSaving(nodeToHv, connection, graph.copy())
    }
  }

  private def calcSaving(
      nodeToHv: Node,
      connection: Connection,
      graph: OsmGraph
  ): Saving = {
    // reconnecting the graph using the common node and a new connection
    val removedEdges = graph.reconnectNodes(nodeToHv, connection)

    val saving: Quantity[Length] =
      removedEdges(0).getDistance
        .add(removedEdges(1).getDistance)
        .subtract(connection.distance)
    Saving(connection, graph, saving)
  }

  // first step of the algorithm
  // return a graph double edge connections to all nodes, except the two closest nodes
  private def firstStep(nodeToHv: Node, connections: Connections): OsmGraph = {
    val graph = new OsmGraph()
    graph.addVertex(nodeToHv)

    // add nodes to graph and connects every node with two edges to the start node
    connections.nodes.foreach { node =>
      graph.addVertex(node)
      graph.addWeightedEdge(nodeToHv, node)
      graph.addWeightedEdge(node, nodeToHv)
    }

    val nearestNeighbour: List[Node] = connections.getNearestNeighbour(nodeToHv)
    val nodeA = nearestNeighbour(0)
    val nodeB = nearestNeighbour(1)

    // adding the two closest nodes directly to the nodeToHv
    graph.reconnectNodes(nodeToHv, connections.getConnection(nodeA, nodeB))

    graph
  }

}
