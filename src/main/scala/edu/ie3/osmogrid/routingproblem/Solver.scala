/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.routingproblem

import edu.ie3.datamodel.graph.DistanceWeightedEdge
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.osmogrid.mv.MvGraphBuilder.MvGraph
import edu.ie3.osmogrid.routingproblem.Definitions.{
  Connections,
  NodeConversion,
  StepResult,
  StepResultOption
}
import edu.ie3.util.osm.model.OsmEntity.Node

import javax.measure.Quantity
import javax.measure.quantity.Length

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
    val stepResult = firstStep(nodeToHv, connections)

    ???
  }

  private def step(
      nodeToHv: Node,
      current: Node,
      connections: Connections,
      notConnectedNodes: List[Node],
      osmGraph: OsmGraph
  ): StepResult = {
    val nearestNeighbors: List[Node] = connections.getNearestNeighbors(current)
    val edges: List[DistanceWeightedEdge] = osmGraph.getSortedEdges(current)

    // calculate all possible result options for this step
    val stepResultOptions: List[StepResultOption] = nearestNeighbors
      .filter(node => notConnectedNodes.contains(node))
      .flatMap { neighbor =>
        calcStepResultOptions(
          nodeToHv,
          current,
          neighbor,
          osmGraph,
          edges,
          connections
        )
      }

    // check the options and return the the result of this step
    checkStepResultOptions(stepResultOptions)
  }

  private def checkStepResultOptions(
      options: List[StepResultOption]
  ): StepResult = {
    ???
  }

  // neighbor should not be connected to another node
  // current should have two edges
  // both can be removed in order to add the neighbor to the graph
  // a connection to the nodeToHv is not removed
  private def calcStepResultOptions(
      nodeToHv: Node,
      current: Node,
      neighbor: Node,
      osmGraph: OsmGraph,
      edges: List[DistanceWeightedEdge],
      connections: Connections
  ): List[StepResultOption] = {
    if (neighbor == nodeToHv || osmGraph.getEdge(current, neighbor) != null) {
      // if neighbor is transition point or the neighbor is already connected to the current node
      // an empty list is returned
      List.empty
    } else {
      edges.flatMap { edge =>
        val source: Node = osmGraph.getEdgeSource(edge)
        val target: Node = osmGraph.getEdgeTarget(edge)

        if (source == nodeToHv || target == nodeToHv) {
          // return nothing if either the source of the target of the edge is the transition point
          None
        } else {
          // create a copy of the graph that can be modified
          val copy = osmGraph.copy()
          val removedEdge = copy.removeEdge(source, target)

          // finds the two connections needed to add the neighbor to the copied graph
          val connectionA = connections.getConnection(source, neighbor)
          val connectionB = connections.getConnection(neighbor, target)
          copy.addConnection(connectionA)
          copy.addConnection(connectionB)

          // calculating the added weight
          val addedWeight: Quantity[Length] = connectionA.distance
            .add(connectionB.distance)
            .subtract(removedEdge.getDistance)

          Some(
            StepResultOption(
              copy,
              neighbor,
              List(connectionA, connectionB),
              removedEdge,
              addedWeight
            )
          )
        }
      }
    }
  }

  /** Method to set up the graph for further calculations. The two closest nodes
    * are connected with one edge to the start point and one edge to each other.
    * A [[Node]] is returned, that can be used as a start point for next steps.
    * Also a list of [[Node]]'s that are not yet connected is returned.
    *
    * @param nodeToHv
    *   start and end point of the final graph
    * @param connections
    *   all [[Connections]] that should be considered
    * @return
    *   a graph, the next node and a list not connected nodes
    */
  private def firstStep(
      nodeToHv: Node,
      connections: Connections
  ): StepResult = {
    val graph = new OsmGraph()
    val nodes: List[Node] = connections.nodes

    // adding all nodes to the graph
    nodes.foreach { node => graph.addVertex(node) }

    // finding the two closest nodes for the transition point
    val nearestNeighbors: List[Node] = connections.getNearestNeighbors(nodeToHv)
    val nodeA = nearestNeighbors(0)
    val nodeB = nearestNeighbors(1)

    // connecting the two closest nodes directly to the transition point
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

    // connecting nodeA and nodeB
    graph.addConnection(connections.getConnection(nodeA, nodeB))

    StepResult(graph, nodeA, nodes.diff(List(nodeToHv, nodeA, nodeB)))
  }
}
