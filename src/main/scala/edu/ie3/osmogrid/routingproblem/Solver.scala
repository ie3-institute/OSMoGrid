/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.routingproblem

import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.osmogrid.mv.MvGraphBuilder.MvGraph
import edu.ie3.osmogrid.routingproblem.Definitions.{
  Connection,
  Connections,
  NodeConversion,
  Saving
}
import edu.ie3.util.osm.model.OsmEntity.Node

import javax.measure.Quantity
import javax.measure.quantity.Length

/** Solver for the routing problem. Uses a combination of savings algorithm,
  * tabu lists and nearest neighbour search.
  */
object Solver {

  // method to solve the routing problem
  def solve(nodeToHv: Node, connections: Connections, conversion: NodeConversion): MvGraph = {
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
  // return a graph and a list of nodes with only one connection to the transition point
  private def firstStep(
      nodeToHv: Node,
      connections: Connections
  ): (OsmGraph, List[Node]) = {
    val graph = new OsmGraph()
    graph.addVertex(nodeToHv)

    // add nodes to graph and connects every node with two edges to the start node
    connections.nodes.foreach { node => graph.addVertex(node) }
    connections.getConnections(nodeToHv).foreach { connection =>
      graph.addConnection(connection)
    }

    val nearestNeighbour: List[Node] = connections.getNearestNeighbour(nodeToHv)
    val nodeA = nearestNeighbour(0)
    val nodeB = nearestNeighbour(1)

    // adding the two closest nodes directly to the nodeToHv
    graph.reconnectNodes(nodeToHv, connections.getConnection(nodeA, nodeB))

    // re-adding one connection between the transition point and nodeA/nodeB
    graph.addWeightedEdge(
      nodeToHv,
      nodeA,
      connections.distance((nodeToHv, nodeA)).distance
    )
    graph.addWeightedEdge(
      nodeToHv,
      nodeB,
      connections.distance((nodeToHv, nodeB)).distance
    )

    (graph, List(nodeA, nodeB))
  }

}
