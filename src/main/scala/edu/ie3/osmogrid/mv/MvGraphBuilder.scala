/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.mv

import edu.ie3.datamodel.graph.DistanceWeightedEdge
import edu.ie3.datamodel.models.input.NodeInput
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.util.geo.GeoUtils
import edu.ie3.util.osm.model.OsmEntity.{Node, Way}
import org.jgrapht.alg.interfaces.ShortestPathAlgorithm.SingleSourcePaths
import org.jgrapht.alg.shortestpath.BFSShortestPath
import tech.units.indriya.quantity.Quantities
import tech.units.indriya.unit.Units

object MvGraphBuilder {

  /** Method to find the closest [[Node]] for a given [[NodeInput]].
    * @param node
    *   for which the closest osm node should be returned
    * @param osmNodes
    *   map containing osm nodes
    * @return
    *   the closest [[Node]]
    */
  def findClosestOsmNode(
      node: NodeInput,
      osmNodes: Map[Long, Node]
  ): NodeConversion = {
    val coordinate = node.getGeoPosition.getCoordinate

    val sortedList = osmNodes.values.toList
      .map { node: Node =>
        (
          node,
          GeoUtils.calcHaversine(coordinate, node.coordinate.getCoordinate)
        )
      }
      .sortBy(_._2)

    NodeConversion(node, sortedList(1)._1)
  }

  // uses the street graph to find all connections between two OSM Nodes
  // returns a list of MvConnections
  // TODO: Testing if the selected shortest path algorithm is the best for our usecase
  def findAllConnections(
      osmGraph: OsmGraph,
      osmNodes: Map[Long, Node]
  ): List[MvConnections] = {
    val shortestPath: BFSShortestPath[Node, DistanceWeightedEdge] =
      new BFSShortestPath(osmGraph)

    val nodes = osmNodes.values.toList
    val paths: List[(Node, SingleSourcePaths[Node, DistanceWeightedEdge])] =
      nodes.map(node => (node, shortestPath.getPaths(node)))

    paths.flatMap(path => {
      val startNode = path._1
      val shortestPaths = path._2

      nodes
        .flatMap { node =>
          if (node != startNode) {
            Some(startNode, shortestPaths.getPath(node))
          } else {
            None
          }
        }
        .map { case (node, graphPath) =>
          MvConnections(
            node,
            graphPath.getEndVertex,
            Quantities.getQuantity(graphPath.getWeight, Units.METRE),
            graphPath
          )
        }
    })
  }

  // builds a street graph
  def buildStreetGraph(ways: Seq[Way], nodes: Map[Long, Node]): OsmGraph = ???

}
