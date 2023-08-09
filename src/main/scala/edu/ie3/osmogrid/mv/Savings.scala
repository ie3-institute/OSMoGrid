/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.mv

import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.osmogrid.mv.MvGraphBuilder.{
  MvConnection,
  MvGraph,
  NodeConversion
}
import edu.ie3.util.osm.model.OsmEntity.Node

import javax.measure.Quantity
import javax.measure.quantity.Length

object Savings {
  final case class Saving(
      usedConnection: MvConnection,
      updatedGraph: OsmGraph,
      saving: Quantity[Length]
  )

  // uses saving algorithm to minimize the connection length
  def savingsAlgorithm(
      nodeToHv: Node,
      nodes: List[Node],
      connections: List[MvConnection],
      conversion: NodeConversion
  ): MvGraph = {
    val graph = new OsmGraph()
    graph.addVertex(nodeToHv)

    // add nodes to graph and connects every node with two edges to the start node
    nodes.foreach { node =>
      graph.addVertex(node)
      graph.addWeightedEdge(nodeToHv, node)
      graph.addWeightedEdge(node, nodeToHv)
    }




    ???
  }

  def calcSavings(
      nodeToHv: Node,
      connections: List[MvConnection],
      graph: OsmGraph
  ): Seq[Saving] = {
    connections.flatMap { connection =>
      val copy = graph.copy()

      // removing one of the two edges
      if (
        copy.getAllEdges(nodeToHv, connection.nodeA).size() > 1
        && copy.getAllEdges(nodeToHv, connection.nodeB).size() > 1
      ) {

        val edgeA = copy.removeEdge(nodeToHv, connection.nodeA)
        val edgeB = copy.removeEdge(nodeToHv, connection.nodeB)

        graph.addWeightedEdge(connection.nodeA, connection.nodeB)

        val saving: Quantity[Length] =
          edgeA.getDistance.add(edgeB.getDistance).subtract(connection.distance)

        Some(Saving(connection, copy, saving))
      } else None
    }
  }

}
