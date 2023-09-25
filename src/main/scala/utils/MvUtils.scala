/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package utils

import edu.ie3.datamodel.models.input.NodeInput
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.osmogrid.routingproblem.Definitions.{
  Connection,
  Connections,
  NodeConversion
}
import edu.ie3.util.geo.GeoUtils
import edu.ie3.util.osm.model.OsmEntity.Node

import scala.jdk.CollectionConverters.CollectionHasAsScala

object MvUtils {

  def createDefinitions(
      nodes: List[NodeInput],
      streetGraph: OsmGraph
  ): (NodeConversion, Connections) = {
    val allOsmNodes: List[Node] = streetGraph.vertexSet().asScala.toList

    // building node conversion
    val nodeConversion: NodeConversion = buildNodeConversion(nodes, allOsmNodes)
    val osmNodes: List[Node] = nodeConversion.conversionToOsm.values.toList

    // finding all unique connections and building connections
    val uniqueConnections = Connections.getAllUniqueCombinations(osmNodes)
    val connections: Connections = Connections(
      osmNodes,
      buildUniqueConnections(uniqueConnections, streetGraph)
    )

    (nodeConversion, connections)
  }

  def buildNodeConversion(
      nodes: List[NodeInput],
      osmNodes: List[Node]
  ): NodeConversion = {
    val conversion: Map[NodeInput, Node] = nodes.map { node =>
      val coordinate = node.getGeoPosition.getCoordinate

      val sortedList = osmNodes
        .map { node: Node =>
          (
            node,
            GeoUtils.calcHaversine(coordinate, node.coordinate.getCoordinate)
          )
        }
        .sortBy(_._2)

      node -> sortedList(1)._1
    }.toMap

    NodeConversion(conversion, conversion.map { case (k, v) => v -> k })
  }

  // uses haversine for now
  // TODO: Calculating distance via shortest path algorithm
  private def buildUniqueConnections(
      uniqueCombinations: List[(Node, Node)],
      streetGraph: OsmGraph
  ): List[Connection] = {
    uniqueCombinations.map { case (nodeA, nodeB) =>
      val distance = GeoUtils.calcHaversine(
        nodeA.coordinate.getCoordinate,
        nodeB.coordinate.getCoordinate
      )
      Connection(nodeA, nodeB, distance, None)
    }
  }
}
