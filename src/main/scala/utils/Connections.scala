/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package utils

import edu.ie3.datamodel.graph.{DistanceWeightedEdge, DistanceWeightedGraph}
import edu.ie3.datamodel.models.input.NodeInput
import edu.ie3.datamodel.models.input.connector.LineInput
import edu.ie3.osmogrid.exception.GridException
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.osmogrid.lv.LvGridGeneratorSupport.GridElements
import edu.ie3.util.geo.GeoUtils
import edu.ie3.util.osm.model.OsmEntity.Node
import org.jgrapht.alg.interfaces.ShortestPathAlgorithm
import org.jgrapht.alg.interfaces.ShortestPathAlgorithm.SingleSourcePaths
import org.jgrapht.alg.shortestpath.DijkstraShortestPath
import org.jgrapht.{Graph, GraphPath}
import org.slf4j.{Logger, LoggerFactory}
import tech.units.indriya.ComparableQuantity
import tech.units.indriya.quantity.Quantities
import tech.units.indriya.unit.Units
import utils.Connections.{Connection, log}
import utils.OsmoGridUtils.getAllUniqueCombinations

import javax.measure.quantity.Length
import scala.collection.mutable
import scala.jdk.CollectionConverters._

/** This utility object contains all known [[Connection]]s.
  *
  * @param elements
  *   list of all elements
  * @param connections
  *   map: element to list of connected elements
  * @param connectionMap
  *   map: (element, element) to actual [[Connection]]
  */
case class Connections[T](
    elements: List[T],
    connections: Map[T, List[T]],
    connectionMap: Map[(T, T), Connection[T]]
) {

  /** @param node
    *   given node
    * @return
    *   a list of all [[Connection]] for a given [[Node]]
    */
  def getConnections(node: T): List[Connection[T]] = {
    connections(node).map { nodeB => (node, nodeB) }.map { tuple =>
      connectionMap(tuple)
    }
  }

  /** @param nodeA
    *   start
    * @param nodeB
    *   end
    * @return
    *   the [[Connection]] between two given [[Node]]s, if the two nodes are not
    *   connected a connection with distance [[Double.MaxValue]] is returned
    */
  def getConnection(nodeA: T, nodeB: T): Connection[T] =
    connectionMap.get((nodeA, nodeB)) match {
      case Some(value) => value
      case None =>
        log.warn(
          s"The given nodes $nodeA and $nodeB are not connected! A Connection with distance ${Double.MaxValue} is returned instead."
        )
        Connection(
          nodeA,
          nodeB,
          Quantities.getQuantity(Double.MaxValue, Units.METRE),
          None
        )
    }

  /** @param nodeA
    *   start
    * @param nodeB
    *   end
    * @return
    *   distance between the given nodes, if the two nodes are not connected
    *   [[Double.MaxValue]] is returned
    */
  def getDistance(nodeA: T, nodeB: T): ComparableQuantity[Length] =
    getConnection(nodeA, nodeB).distance

  /** @param node
    *   given node
    * @return
    *   a list of neighboring node sorted by their distance
    */
  def getNearestNeighbors(node: T): List[T] = {
    connections(node)
      .map { nodeB => nodeB -> getDistance(node, nodeB) }
      .sortBy(_._2)
      .map { case (node, _) => node }
  }

  /** @param node
    *   given node
    * @param n
    *   number of neighbors
    * @return
    *   a list of the n nearest neighboring node sorted by their distance
    */
  def getNearestNeighbors(node: T, n: Int): List[T] = {
    connections(node)
      .map { nodeB => nodeB -> getDistance(node, nodeB) }
      .sortBy(_._2)
      .map { case (node, _) => node }
      .slice(0, n)
  }
}

object Connections {
  val log: Logger = LoggerFactory.getLogger(Connections.getClass)

  /** Utility object for connections.
    *
    * @param nodeA
    *   start of connection
    * @param nodeB
    *   end of connection
    * @param distance
    *   distance of the connection
    * @param path
    *   optional graph path
    */
  final case class Connection[T](
      nodeA: T,
      nodeB: T,
      distance: ComparableQuantity[Length],
      path: Option[GraphPath[T, DistanceWeightedEdge]]
  )

  def apply(
      elements: GridElements,
      lines: Seq[LineInput]
  ): Connections[NodeInput] = {
    val graph: DistanceWeightedGraph = new DistanceWeightedGraph()

    // adding all nodes to the graph
    val nodes: List[NodeInput] =
      (elements.nodes.values ++ elements.substations.values).toList
    nodes.foreach { n => graph.addVertex(n) }

    lines.foreach { line =>
      val edge = graph.addEdge(line.getNodeA, line.getNodeB)
      graph.setEdgeWeight(edge, line.getLength)
    }

    val shortestPath =
      new DijkstraShortestPath[NodeInput, DistanceWeightedEdge](graph)

    val connectionList: List[Connection[NodeInput]] =
      buildUniqueConnections(graph, shortestPath)
    Connections(nodes, connectionList)
  }

  def apply[T](
      elements: List[T],
      connections: List[Connection[T]]
  ): Connections[T] = {
    val connectionMap: mutable.Map[T, List[T]] =
      new mutable.HashMap[T, List[T]]
    elements.map { node =>
      connectionMap.addOne(node -> List())
    }

    connections.foreach { connection =>
      val listA: List[T] = connectionMap(connection.nodeA)
      val listB: List[T] = connectionMap(connection.nodeB)

      connectionMap.addOne(
        connection.nodeA -> listA.appended(connection.nodeB)
      )
      connectionMap.addOne(
        connection.nodeB -> listB.appended(connection.nodeA)
      )
    }

    val distanceMap: Map[(T, T), Connection[T]] = connections.map {
      connection =>
        (connection.nodeA, connection.nodeB) -> connection
    }.toMap

    val distanceMapAlt: Map[(T, T), Connection[T]] = distanceMap.map {
      case (tuple, connection) =>
        (tuple._2, tuple._1) -> connection
    }

    Connections(elements, connectionMap.toMap, distanceMap ++ distanceMapAlt)
  }

  /** Method for creating unique [[Connection]]s using a
    * [[ShortestPathAlgorithm]].
    * @param graph
    *   with paths
    * @param shortestPath
    *   algorithm to calculate the paths
    * @tparam T
    *   type of vertexes
    * @return
    *   a list of unique [[Connection]]s
    */
  def buildUniqueConnections[T](
      graph: Graph[T, DistanceWeightedEdge],
      shortestPath: ShortestPathAlgorithm[T, DistanceWeightedEdge]
  ): List[Connection[T]] = {
    val vertexes = graph.vertexSet().asScala
    val paths = vertexes.map { v => v -> shortestPath.getPaths(v) }.toMap

    getAllUniqueCombinations(graph.vertexSet().asScala.toList).map {
      case (nodeA, nodeB) =>
        val path = paths(nodeA).getPath(nodeB)

        if (path == null) {
          val unconnected =
            if (graph.edgesOf(nodeA).size() == 0) nodeA else nodeB
          throw GridException(
            s"No path could be found between $nodeA and $nodeB, because the node $unconnected is not connected to the graph."
          )
        }

        Connection(
          nodeA,
          nodeB,
          Quantities.getQuantity(path.getWeight, Units.METRE),
          Some(path)
        )
    }
  }

  /** Method for creating unique [[Connection]]s.
    *
    * @param uniqueCombinations
    *   list of all unique combinations of [[Node]]s
    * @param streetGraph
    *   containing connections between [[Node]]s
    * @return
    *   a list of unique [[Connection]]s
    */
  def buildUniqueConnections(
      uniqueCombinations: List[(Node, Node)],
      streetGraph: OsmGraph
  ): List[Connection[Node]] = {
    uniqueCombinations.map { case (nodeA, nodeB) =>
      val distance = GeoUtils.calcHaversine(
        nodeA.coordinate.getCoordinate,
        nodeB.coordinate.getCoordinate
      )
      Connection(nodeA, nodeB, distance, None)
    }
  }
}
