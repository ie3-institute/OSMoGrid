/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package utils

import edu.ie3.datamodel.graph.DistanceWeightedEdge
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.util.osm.model.OsmEntity.Node
import utils.MvUtils.{Connection, Connections}

import javax.measure.Quantity
import javax.measure.quantity.Length
import scala.jdk.CollectionConverters.CollectionHasAsScala

/** Solver for the routing problem. Uses a combination of savings algorithm,
  * tabu lists and nearest neighbour search.
  */
object Solver {
  final case class StepResult(
      graph: OsmGraph,
      nextNode: Node,
      notConnectedNodes: List[Node]
  )

  final case class StepResultOption(
      graph: OsmGraph,
      nextNode: Node,
      usedConnections: List[Connection],
      removedEdge: DistanceWeightedEdge,
      addedWeight: Quantity[Length]
  )

  /** Method to solve the routing problem.
    * @param nodeToHv
    *   transition point (start and end point of the graph)
    * @param connections
    *   all [[Connections]] that should be considered
    * @param neighborCount
    *   number of closest vertexes for which connected edges should be
    *   considered (default: 5)
    * @return
    */
  def solve(
      nodeToHv: Node,
      connections: Connections
  )(implicit neighborCount: Int = 5): OsmGraph = {
    val graph = new OsmGraph()
    connections.nodes.foreach(n => graph.addVertex(n))

    if (connections.nodes.size == 1) {
      graph
    } else if (connections.nodes.size < 3) {
      val node = connections.nodes.filter(n => n != nodeToHv)(0)
      graph.addWeightedEdge(
        nodeToHv,
        node,
        connections.connectionMap((nodeToHv, node)).distance
      )
      graph
    } else {
      // calculating the first step
      var stepResult: StepResult = firstStep(nodeToHv, connections)
      var finished = false

      // calculating next steps
      while (!finished && stepResult.notConnectedNodes.nonEmpty) {
        step(
          nodeToHv,
          stepResult,
          connections,
          neighborCount
        ) match {
          case Some(value) => stepResult = value
          case None        =>
            // when this happens the resulting graph still has unconnected nodes
            System.out.print(
              s"\nFinished with: ${stepResult.notConnectedNodes}"
            )
            finished = true
        }
      }

      stepResult.graph
    }
  }

  /** Method to calculate the next step of the solving algorithm.
    * @param nodeToHv
    *   transition point (start and end point of the graph)
    * @param stepResult
    *   the result of the previous step
    * @param connections
    *   all [[Connections]] that should be considered
    * @param neighborCount
    *   number of closest vertexes for which connected edges should be
    *   considered
    * @return
    *   an option for a [[StepResult]]
    */
  private def step(
      nodeToHv: Node,
      stepResult: StepResult,
      connections: Connections,
      neighborCount: Int
  ): Option[StepResult] = {
    // extracting some information from the previous step
    val current = stepResult.nextNode
    val graph = stepResult.graph
    val notConnectedNodes = stepResult.notConnectedNodes
    val nearestNeighbors: List[Node] = connections.getNearestNeighbors(current)

    // calculate all possible result options for this step
    val stepResultOptions: List[StepResultOption] = nearestNeighbors
      .filter(node => notConnectedNodes.contains(node))
      .flatMap { neighbor =>
        // edges that can be removed
        val edges = connections
          .getNearestNeighbors(neighbor, neighborCount)
          .flatMap { v => graph.edgesOf(v).asScala }
          .toSet

        calcStepResultOptions(
          nodeToHv,
          current,
          neighbor,
          graph,
          edges,
          connections
        )
      }

    // evaluating the options and returning an option for a StepResult
    evaluateStepResultOptions(
      stepResultOptions,
      notConnectedNodes
    )
  }

  /** Method for evaluating [[StepResultOption]]s. This method will filter out
    * results that contain a graph with intersecting edges and/or a graph where
    * vertexes have more than two edges. After the filtering, the remaining
    * results are sorted by the weight that is added by the result and the
    * result that adds the least weight will be converted into an option of a
    * [[StepResult]]. If all results are filtered out, a [[None]] is returned
    * @param options
    *   that should be evaluated
    * @param notConnectedNodes
    *   a list of [[Node]]s that are not yet connected to the graph
    * @return
    *   an option for a [[StepResult]]
    */
  private def evaluateStepResultOptions(
      options: List[StepResultOption],
      notConnectedNodes: List[Node]
  ): Option[StepResult] = {
    val filtered = options
      .filter(option => !option.graph.containsEdgeIntersection())
      .filter(options => !options.graph.tooManyVertexConnections())
      .sortBy(option => option.addedWeight.getValue.doubleValue())

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

  /** Calculation for a step. The result of the calculation is a list of
    * [[StepResultOption]]s.
    * @param nodeToHv
    *   transition point (start and end point of the graph)
    * @param current
    *   the last connected [[Node]]
    * @param neighbor
    *   a [[Node]] that can be connected to the graph
    * @param osmGraph
    *   the graph before this step
    * @param edges
    *   a list of [[DistanceWeightedEdge]]s that could be removed in order to
    *   connect the neighbor to the graph
    * @param connections
    *   all [[Connections]] that should be considered
    * @return
    *   a list of [[StepResultOption]]s that represent possible results of the
    *   current step
    */
  private def calcStepResultOptions(
      nodeToHv: Node,
      current: Node,
      neighbor: Node,
      osmGraph: OsmGraph,
      edges: Set[DistanceWeightedEdge],
      connections: Connections
  ): List[StepResultOption] = {
    if (neighbor == nodeToHv || osmGraph.getEdge(current, neighbor) != null) {
      // if neighbor is transition point or the neighbor is already connected to the current node
      // an empty list is returned
      List.empty
    } else
      {
        edges.flatMap { edge =>
          val source: Node = osmGraph.getEdgeSource(edge)
          val target: Node = osmGraph.getEdgeTarget(edge)

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
      }.toList
  }

  /** Method to set up the graph for further calculations. The two closest nodes
    * are connected with one edge to the start point and one edge to each other.
    * A [[Node]] is returned, that can be used as a start point for next steps.
    * Also a list of [[Node]]'s that are not yet connected is returned.
    *
    * @param nodeToHv
    *   transition point (start and end point of the graph)
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
