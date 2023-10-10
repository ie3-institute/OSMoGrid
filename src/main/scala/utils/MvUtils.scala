/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package utils

import edu.ie3.datamodel.graph.DistanceWeightedEdge
import edu.ie3.datamodel.models.input.NodeInput
import edu.ie3.osmogrid.graph.OsmGraph
import GridConversion.NodeConversion
import edu.ie3.util.geo.GeoUtils
import edu.ie3.util.osm.model.OsmEntity.Node
import org.jgrapht.GraphPath
import org.slf4j.{Logger, LoggerFactory}
import tech.units.indriya.ComparableQuantity
import tech.units.indriya.quantity.Quantities
import tech.units.indriya.unit.Units
import utils.VoronoiUtils.VoronoiPolygon

import java.util
import javax.measure.quantity.Length
import scala.collection.mutable
import scala.jdk.CollectionConverters.CollectionHasAsScala

/** Utility object for mv generation.
  */
object MvUtils {
  val log: Logger = LoggerFactory.getLogger(MvUtils.getClass)

  /** Utility method for creating [[NodeConversion]] and [[Connections]]
    * utilities. <p> NOTICE: for correct operation -> number of vertexes >=
    * number of [[NodeInput]]s
    * @param nodes
    *   all psdm nodes
    * @param streetGraph
    *   graph with osm nodes and connections
    * @return
    *   utility objects
    */
  private def createDefinitions(
      nodes: List[NodeInput],
      streetGraph: OsmGraph
  ): (NodeConversion, Connections) = {
    val allOsmNodes: List[Node] = streetGraph.vertexSet().asScala.toList

    // building node conversion
    val nodeConversion: NodeConversion = NodeConversion(nodes, allOsmNodes)
    val osmNodes: List[Node] = nodeConversion.conversionToOsm.values.toList

    // finding all unique connections and building connections
    val uniqueConnections = getAllUniqueCombinations(osmNodes)
    val connections: Connections = Connections(
      osmNodes,
      buildUniqueConnections(uniqueConnections, streetGraph)
    )

    (nodeConversion, connections)
  }

  /** Method for generating a mv graph structure.
    *
    * @param nr
    *   subnet number
    * @param voronoiPolygon
    *   polygon with nodes
    * @param streetGraph
    *   complete osm street graph
    * @return
    *   a osm graph and the used node conversion object
    */
  def generateMvGraph(
      nr: Int,
      voronoiPolygon: VoronoiPolygon,
      streetGraph: OsmGraph
  ): (OsmGraph, NodeConversion) = {
    log.debug(s"Start graph generation for grid $nr.")

    // if this voronoi polygon contains a polygon, we can reduce the complete street graph in order to reduce the calculation time
    val reducedStreetGraph: OsmGraph = voronoiPolygon.polygon
      .map { polygon => streetGraph.subGraph(polygon) }
      .getOrElse(streetGraph)

    // creating necessary utility objects
    val (nodeConversion, connections) =
      MvUtils.createDefinitions(voronoiPolygon.allNodes, reducedStreetGraph)

    val transitionNode: Node = nodeConversion.getOsmNode(
      voronoiPolygon.transitionPointToHigherVoltLvl
    )

    // using the solver to solve the routing problem
    val graph: OsmGraph = Solver.solve(
      transitionNode,
      connections
    )

    (graph, nodeConversion)
  }

  /** Method for creating unique [[Connection]]s.
    * @param uniqueCombinations
    *   list of all unique combinations of [[Node]]s
    * @param streetGraph
    *   containing connections between [[Node]]s
    * @return
    *   a list of unique [[Connection]]s
    */
  // TODO: Calculating distances via shortest path algorithm, instead of haversine
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

  /** Method to find all unique connections. If less than two nodes are
    * provided, an empty list is returned. <p> Uniqueness: a -> b == b -> a
    *
    * @param nodes
    *   list of nodes
    * @return
    *   a list of all unique connections
    */
  def getAllUniqueCombinations(
      nodes: List[Node]
  ): List[(Node, Node)] = {
    if (nodes.size < 2) {
      List.empty
    } else if (nodes.size == 2) {
      List((nodes(0), nodes(1)))
    } else {
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

  /** This utility object contains all known [[Connection]]s.
    * @param nodes
    *   list of all osm nodes
    * @param connections
    *   map: node to list of connected nodes
    * @param connectionMap
    *   map: (node, node) to actual [[Connection]]
    */
  final case class Connections(
      nodes: List[Node],
      connections: Map[Node, List[Node]],
      connectionMap: Map[(Node, Node), Connection]
  ) {

    /** @param node
      *   given node
      * @return
      *   a list of all [[Connection]] for a given [[Node]]
      */
    def getConnections(node: Node): List[Connection] = {
      connections(node).map { nodeB => (node, nodeB) }.map { tuple =>
        connectionMap(tuple)
      }
    }

    /** @param nodeA
      *   start
      * @param nodeB
      *   end
      * @return
      *   the [[Connection]] between two given [[Node]]s, if the two nodes are
      *   not connected a connection with distance [[Double.MaxValue]] is
      *   returned
      */
    def getConnection(nodeA: Node, nodeB: Node): Connection =
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
    def getDistance(nodeA: Node, nodeB: Node): ComparableQuantity[Length] =
      getConnection(nodeA, nodeB).distance

    /** @param node
      *   given node
      * @return
      *   a list of neighboring node sorted by their distance
      */
    def getNearestNeighbors(node: Node): List[Node] = {
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
    def getNearestNeighbors(node: Node, n: Int): List[Node] = {
      connections(node)
        .map { nodeB => nodeB -> getDistance(node, nodeB) }
        .sortBy(_._2)
        .map { case (node, _) => node }
        .slice(0, n)
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
  }

  /** Utility object for connections.
    * @param nodeA
    *   start of connection
    * @param nodeB
    *   end of connection
    * @param distance
    *   distance of the connection
    * @param path
    *   optional graph path
    */
  final case class Connection(
      nodeA: Node,
      nodeB: Node,
      distance: ComparableQuantity[Length],
      path: Option[GraphPath[Node, DistanceWeightedEdge]]
  )
}
