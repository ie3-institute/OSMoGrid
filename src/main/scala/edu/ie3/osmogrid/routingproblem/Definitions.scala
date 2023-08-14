/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.routingproblem

import edu.ie3.datamodel.graph.DistanceWeightedEdge
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.util.osm.model.OsmEntity.Node
import org.jgrapht.GraphPath
import tech.units.indriya.ComparableQuantity

import javax.measure.Quantity
import javax.measure.quantity.Length
import scala.collection.mutable

/** Definitions for a routing problem.
  */
object Definitions {
  final case class Connection(
      nodeA: Node,
      nodeB: Node,
      distance: ComparableQuantity[Length],
      path: Option[GraphPath[Node, DistanceWeightedEdge]]
  )

  final case class Connections(
      nodes: List[Node],
      connections: Map[Node, List[Node]],
      distance: Map[(Node, Node), Connection]
  ) {
    def getConnection(nodeA: Node, nodeB: Node): Connection =
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
      val connectionMap: mutable.Map[Node, List[Node]] =
        new mutable.HashMap[Node, List[Node]]
      osmNodes.map { node =>
        connectionMap.addOne(node -> List())
      }

      connections.foreach { connection =>
        val listA: List[Node] = connectionMap(connection.nodeA)
        val listB: List[Node] = connectionMap(connection.nodeB)

        connectionMap.addOne(
          connection.nodeA -> listA.appended(connection.nodeB)
        )
        connectionMap.addOne(
          connection.nodeB -> listB.appended(connection.nodeA)
        )
      }

      val distanceMap: Map[(Node, Node), Connection] = connections.map {
        connection =>
          (connection.nodeA, connection.nodeB) -> connection
      }.toMap

      Connections(osmNodes, connectionMap.toMap, distanceMap)
    }
  }

  final case class Saving(
      usedConnection: Connection,
      updatedGraph: OsmGraph,
      saving: Quantity[Length]
  )

}
