/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.mv

import edu.ie3.datamodel.graph.DistanceWeightedEdge
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.osmogrid.mv.MvGraphBuilder.{MvGraph, NodeConversion}
import edu.ie3.util.osm.model.OsmEntity.Node
import org.jgrapht.GraphPath
import tech.units.indriya.ComparableQuantity

import javax.measure.Quantity
import javax.measure.quantity.Length

object Savings {
  final case class Connection(
      nodeA: Node,
      nodeB: Node,
      distance: ComparableQuantity[Length],
      path: Option[GraphPath[Node, DistanceWeightedEdge]]
  )

  final case class Connections(
      connections: Map[Node, List[Node]],
      distance: Map[(Node, Node), Connection]
  ) {
    private def getConnection(nodeA: Node, nodeB: Node): Connection =
      distance.get((nodeA, nodeB)) match {
        case Some(value) => value
        case None        => distance((nodeB, nodeA))
      }

    def getDistance(nodeA: Node, nodeB: Node): ComparableQuantity[Length] =
      getConnection(nodeA, nodeB).distance

    def getNearestNeighbour(node: Node): List[Node] = {
      connections(node)
        .map { nodeB => node -> getDistance(node, nodeB) }
        .sortBy(_._2)
        .map { case (node, _) => node }
    }
  }

  object Connections {
    def apply(
        osmNodes: List[Node],
        connections: List[Connection]
    ): Connections = {
      val connectionMap: Map[Node, List[Node]] = osmNodes.map { node =>
        node -> List()
      }.toMap

      connections.foreach { connection =>
        val listA: List[Node] = connectionMap(connection.nodeA)
        val listB: List[Node] = connectionMap(connection.nodeB)

        connectionMap ++ (connection.nodeA -> listA.appended(connection.nodeB))
        connectionMap ++ (connection.nodeB -> listB.appended(connection.nodeA))
      }

      val distanceMap: Map[(Node, Node), Connection] = connections.map {
        connection =>
          (connection.nodeA, connection.nodeB) -> connection
      }.toMap

      Connections(connectionMap, distanceMap)
    }
  }

  final case class Saving(
      usedConnection: Connection,
      updatedGraph: OsmGraph,
      saving: Quantity[Length]
  )

  // uses saving algorithm to minimize the connection length
  def savingsAlgorithm(
      nodeToHv: Node,
      nodes: List[Node],
      connections: Connections,
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
      connections: List[Connection],
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

        graph.addWeightedEdge(
          connection.nodeA,
          connection.nodeB,
          connection.distance
        )

        val saving: Quantity[Length] =
          edgeA.getDistance.add(edgeB.getDistance).subtract(connection.distance)

        Some(Saving(connection, copy, saving))
      } else None
    }
  }

}
