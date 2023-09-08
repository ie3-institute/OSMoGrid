/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.routingproblem

import edu.ie3.datamodel.graph.DistanceWeightedEdge
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.osmogrid.routingproblem.Definitions.{
  Connections,
  StepResult,
  StepResultOption
}
import edu.ie3.util.osm.model.OsmEntity.Node
import utils.GraphUtils

import javax.measure.Quantity
import javax.measure.quantity.Length
import scala.collection.parallel.CollectionConverters.ImmutableIterableIsParallelizable
import scala.jdk.CollectionConverters.CollectionHasAsScala

/** Solver for the routing problem. Uses a combination of savings algorithm,
  * tabu lists and nearest neighbour search.
  */
object Solver {
  val draw = false
  val draw_options = 0
  val draw_all = false
  var step: Int = 1
  val width = 800
  val height = 600

  // method to solve the routing problem
  def solve(
      nodeToHv: Node,
      connections: Connections
  ): OsmGraph = {
    if (connections.nodes.size < 3) {
      val graph = new OsmGraph()
      connections.nodes.foreach(n => graph.addVertex(n))
      graph
    } else {
      // calculating the first step
      var stepResult: StepResult = firstStep(nodeToHv, connections)
      var finished = false

      // calculating next steps
      while (!finished && stepResult.notConnectedNodes.nonEmpty) {
        if (draw) {
          GraphUtils.draw(
            stepResult.graph,
            s"graph_after_step_$step.png",
            width,
            height
          )
        }
        step += 1

        step(
          nodeToHv,
          stepResult,
          connections
        ) match {
          case Some(value) => stepResult = value
          case None =>
            System.out.print(
              s"\nFinished with: ${stepResult.notConnectedNodes}"
            )
            finished = true
        }
      }

      val graph = stepResult.graph

      if (draw) {
        GraphUtils.draw(graph, s"graph_after_step_$step.png", width, height)
      }

      // finishing the mv graph
      // reconnectNodes(graph, connections)

      graph
    }
  }

  private def reconnectNodes(
      graph: OsmGraph,
      connections: Connections
  ): OsmGraph = {
    System.out.print(s"\nNot implemented!")

    graph
  }

  private def finishMvGraph(
      graph: OsmGraph,
      notConnectedNodes: List[Node],
      connections: Connections
  ): OsmGraph = {
    if (notConnectedNodes.isEmpty) {
      graph
    } else {
      notConnectedNodes.foreach { node =>
        connectNode(graph, node, connections)
      }
      graph
    }
  }

  // connects a node to the graph
  private def connectNode(
      graph: OsmGraph,
      node: Node,
      connections: Connections
  ): Unit = {
    val neighbors = connections.getNearestNeighbors(node)
    val edges = graph.edgesOf(neighbors(0)).asScala.toList

    System.out.print(s"\nNot implemented! -> $node")
  }

  private def step(
      nodeToHv: Node,
      stepResult: StepResult,
      connections: Connections
  ): Option[StepResult] = {
    val nodeSize = connections.nodes.size

    val current = stepResult.nextNode
    val graph = stepResult.graph
    val notConnectedNodes = stepResult.notConnectedNodes

    val nearestNeighbors: List[Node] = connections.getNearestNeighbors(current)
    val edges: List[DistanceWeightedEdge] = graph.edgeSet().asScala.toList

    // calculate all possible result options for this step
    val stepResultOptions: List[StepResultOption] = nearestNeighbors
      .filter(node => notConnectedNodes.contains(node))
      .flatMap { neighbor =>
        val nearest = connections
          .getNearestNeighbors(neighbor)
          .slice(0, nodeSize / 15)
          .flatMap { v => graph.edgesOf(v).asScala }

        calcStepResultOptions(
          nodeToHv,
          current,
          neighbor,
          graph,
          nearest,
          connections
        )
      }

    // check the options and return the the result of this step
    evaluateStepResultOptions(
      stepResultOptions,
      notConnectedNodes
    )
  }

  private def evaluateStepResultOptions(
      options: List[StepResultOption],
      notConnectedNodes: List[Node]
  ): Option[StepResult] = {
    val filtered = options
      .filter(option => !option.graph.containsEdgeIntersection())
      .filter(options => !options.graph.tooManyVertexConnections())
      .sortBy(option => option.addedWeight.getValue.doubleValue())

    if (draw && (draw_all || step == draw_options)) {
      filtered.map(o => o.graph).zipWithIndex.foreach { case (graph, i) =>
        GraphUtils.draw(
          graph,
          s"graph_after_step_${step}_option_$i.png",
          width,
          height
        )
      }
    }

    filtered.headOption match {
      case Some(stepResultOption) =>
        Some(
          StepResult(
            stepResultOption.graph,
            stepResultOption.nextNode,
            notConnectedNodes.diff(Seq(stepResultOption.nextNode))
          )
        )
      case None =>
        System.out.print("\nNo step result options were found.")
        None
    }
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
