/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.routingproblem

import edu.ie3.datamodel.graph.DistanceWeightedEdge
import edu.ie3.osmogrid.exception.GraphModifyException
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.osmogrid.mv.MvGraphBuilder.MvGraph
import edu.ie3.osmogrid.routingproblem.Definitions.{
  Connection,
  Connections,
  NodeConversion,
  Saving
}
import edu.ie3.util.osm.model.OsmEntity.Node
import tech.units.indriya.quantity.Quantities
import tech.units.indriya.unit.Units

import javax.measure.Quantity
import javax.measure.quantity.Length
import scala.jdk.CollectionConverters._

/** Solver for the routing problem. Uses a combination of savings algorithm,
  * tabu lists and nearest neighbour search.
  */
object Solver {

  // method to solve the routing problem
  def solve(
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
      doubleEdge: List[DistanceWeightedEdge],
      graph: OsmGraph
  ): Seq[Saving] = {
    connections.map { connection =>
      calcSaving(nodeToHv, connection, doubleEdge, graph.copy())
    }
  }

  private def calcSaving(
      nodeToHv: Node,
      connection: Connection,
      doubleEdge: List[DistanceWeightedEdge],
      graph: OsmGraph
  ): Saving = {
    // reconnecting the graph using the common node and a new connection
    val removedEdges = graph.reconnectNodes(nodeToHv, connection, doubleEdge)

    val saving: Quantity[Length] =
      removedEdges(0).getDistance
        .add(removedEdges(1).getDistance)
        .subtract(connection.distance)
    ???
  }

  private def step(
      nodeToHv: Node,
      current: Node,
      connections: Connections,
      doubleEdges: List[DistanceWeightedEdge],
      osmGraph: OsmGraph
  ): (OsmGraph, Node, List[DistanceWeightedEdge]) = {

    val nearestNeighbors: List[Node] = connections.getNearestNeighbors(current)
    val edges: List[DistanceWeightedEdge] = osmGraph.getSortedEdges(current)
    val totalWeight = osmGraph.calcTotalWeight()

    val savings: List[Saving] = nearestNeighbors.flatMap { node =>
      calcSavings(
        nodeToHv,
        current,
        node,
        osmGraph,
        edges,
        doubleEdges,
        connections
      )
    }

    ???
  }

  private def calcSavings(
      nodeToHv: Node,
      current: Node,
      neighbor: Node,
      osmGraph: OsmGraph,
      edges: List[DistanceWeightedEdge],
      doubleEdges: List[DistanceWeightedEdge],
      connections: Connections
  ): List[Saving] = {

    val edge = osmGraph.getEdge(current, neighbor)

    if (edge != null && !doubleEdges.contains(edge)) {
      // if the current node is connected to the neighbor with only one edge, no further calculation is needed
      List.empty
    } else {
      edges.flatMap { edge =>
        val source: Node = osmGraph.getEdgeSource(edge)
        val target: Node = osmGraph.getEdgeTarget(edge)

        if (source == nodeToHv || target == nodeToHv) {
          // return nothing if either the source of the target of the edge is the transition point
          None
        } else {
          val copy = osmGraph.copy()
          copy.removeEdge(source, target)
          val removedDoubleEdge = copy.removeEdge(nodeToHv, neighbor)

          val (connection1, connection2): (Connection, Connection) =
            if (source == current) {
              (
                connections.getConnection(current, neighbor),
                connections.getConnection(neighbor, target)
              )
            } else if (target == current) {
              (
                connections.getConnection(target, neighbor),
                connections.getConnection(neighbor, current)
              )
            } else {
              throw GraphModifyException(
                s"Found edge $edge is not connected to the given vertex $current. This should not happen!"
              )
            }

          copy.addConnection(connection1)
          copy.addConnection(connection2)

          val saving = removedDoubleEdge.getDistance
            .multiply(2)
            .subtract(connection1.distance.add(connection2.distance))

          Some(
            Saving(
              connection1,
              connection2,
              edge,
              Some(removedDoubleEdge),
              copy,
              saving
            )
          )
        }
      }
    }
  }

  /** Method to set up the graph for further calculations. The two closest nodes
    * are connected with one edge to the start point and one edge to each other.
    * A [[Node]] is returned, that can be used as a start point for next steps.
    * Also a list of edges, which should be considered as double edges, is
    * returned.
    * @param nodeToHv
    *   start and end point of the final graph
    * @param connections
    *   all [[Connections]] that should be considered
    * @return
    *   a graph, the next node and a list containing double edges
    */
  private def firstStep(
      nodeToHv: Node,
      connections: Connections
  ): (OsmGraph, Node, List[DistanceWeightedEdge]) = {
    val graph = new OsmGraph()
    graph.addVertex(nodeToHv)

    // add nodes to graph and connects every node with two edges to the start node
    connections.nodes.foreach { node => graph.addVertex(node) }
    val doubleEdge = connections.getConnections(nodeToHv).map { connection =>
      graph.addConnection(connection)
    }

    val nearestNeighbors: List[Node] = connections.getNearestNeighbors(nodeToHv)
    val nodeA = nearestNeighbors(0)
    val nodeB = nearestNeighbors(1)

    // adding the two closest nodes directly to the nodeToHv
    val updatedDoubleEdge = graph.reconnectNodes(
      nodeToHv,
      connections.getConnection(nodeA, nodeB),
      doubleEdge
    )

    // re-adding one connection between the transition point and nodeA/nodeB
    graph.addWeightedEdge(
      nodeToHv,
      nodeA,
      connections.connectionMap((nodeToHv, nodeA)).distance
    )
    graph.addWeightedEdge(
      nodeToHv,
      nodeB,
      connections.connectionMap((nodeToHv, nodeB)).distance
    )

    (graph, nodeA, updatedDoubleEdge)
  }

}
