/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.routingproblem

import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.osmogrid.routingproblem.Definitions.Connections
import edu.ie3.util.osm.model.OsmEntity.Node

import scala.jdk.CollectionConverters._

class Optimizer {

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
}
