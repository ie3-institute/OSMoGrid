/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.routingproblem

import edu.ie3.datamodel.graph.DistanceWeightedEdge
import edu.ie3.datamodel.models.input.NodeInput
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.util.osm.model.OsmEntity.Node
import org.jgrapht.GraphPath
import tech.units.indriya.ComparableQuantity

import java.util
import javax.measure.Quantity
import javax.measure.quantity.Length
import scala.collection.mutable
import scala.jdk.CollectionConverters._

/** Definitions for a routing problem.
  */
object Definitions {
  final case class NodeConversion(
      conversionToOsm: Map[NodeInput, Node],
      conversionToPSDM: Map[Node, NodeInput]
  ) {
    def getOsmNode(node: NodeInput): Node = {
      conversionToOsm(node)
    }

    def getOsmNodes(nodes: List[NodeInput]): List[Node] = {
      nodes.map { node => conversionToOsm(node) }
    }

    def getPSDMNode(node: Node): NodeInput = {
      conversionToPSDM(node)
    }

    def getPSDMNodes(nodes: List[Node]): List[NodeInput] = {
      nodes.map { node => conversionToPSDM(node) }
    }
  }

  final case class Connections(
      nodes: List[Node],
      connections: Map[Node, List[Node]],
      connectionMap: Map[(Node, Node), Connection]
  ) {
    def getConnections(node: Node): List[Connection] = {
      connections(node).map { nodeB => (node, nodeB) }.map { tuple =>
        connectionMap(tuple)
      }
    }

    def getConnection(nodeA: Node, nodeB: Node): Connection =
      connectionMap.get((nodeA, nodeB)) match {
        case Some(value) => value
        case None        => connectionMap((nodeB, nodeA))
      }

    def getDistance(nodeA: Node, nodeB: Node): ComparableQuantity[Length] =
      getConnection(nodeA, nodeB).distance

    def getNearestNeighbors(node: Node): List[Node] = {
      connections(node)
        .map { nodeB => nodeB -> getDistance(node, nodeB) }
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

      val distanceMapAlt: Map[(Node, Node), Connection] = distanceMap.map {
        case (tuple, connection) =>
          (tuple._2, tuple._1) -> connection
      }

      Connections(osmNodes, connectionMap.toMap, distanceMap ++ distanceMapAlt)
    }

    // used to get all possible unique connections (a -> b == b -> a)
    def getAllUniqueCombinations(
        nodes: List[Node]
    ): List[(Node, Node)] = {
      val connections: util.List[(Node, Node)] =
        new util.ArrayList[(Node, Node)]

      // algorithm to find all unique combinations
      nodes.foreach(nodeA => {
        nodes.foreach(nodeB => {
          // it makes no sense to connect a node to itself => nodeA and nodeB cannot be the same
          if (nodeA != nodeB) {
            // two combinations possible
            val t1 = (nodeA, nodeB)
            val t2 = (nodeB, nodeA)

            // if none of the combinations is already added, the first combination is added
            if (!connections.contains(t1) && !connections.contains(t2)) {
              connections.add(t1)
            }
          }
        })
      })

      // returns all unique connections
      connections.asScala.toList
    }
  }

  final case class Connection(
      nodeA: Node,
      nodeB: Node,
      distance: ComparableQuantity[Length],
      path: Option[GraphPath[Node, DistanceWeightedEdge]]
  )

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

}
