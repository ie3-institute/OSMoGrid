/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package utils

import edu.ie3.datamodel.graph.DistanceWeightedEdge
import edu.ie3.datamodel.models.input.NodeInput
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.util.geo.GeoUtils
import edu.ie3.util.osm.model.OsmEntity.Node
import org.jgrapht.GraphPath
import org.slf4j.{Logger, LoggerFactory}
import tech.units.indriya.ComparableQuantity
import tech.units.indriya.quantity.Quantities
import tech.units.indriya.unit.Units

import java.util
import javax.measure.quantity.Length
import scala.collection.mutable
import scala.jdk.CollectionConverters.CollectionHasAsScala

object MvUtils {
  val log: Logger = LoggerFactory.getLogger(MvUtils.getClass)

  def createDefinitions(
      nodes: List[NodeInput],
      streetGraph: OsmGraph
  ): (NodeConversion, Connections) = {
    val allOsmNodes: List[Node] = streetGraph.vertexSet().asScala.toList

    // building node conversion
    val nodeConversion: NodeConversion = buildNodeConversion(nodes, allOsmNodes)
    val osmNodes: List[Node] = nodeConversion.conversionToOsm.values.toList

    // finding all unique connections and building connections
    val uniqueConnections = getAllUniqueCombinations(osmNodes)
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

  // used to get all possible unique connections ()

  /** Method to find all unique connections. <p> Uniqueness: a -> b == b -> a
    *
    * @param nodes
    *   list of nodes
    * @return
    *   a list of all unique connections
    */
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

  /** This utility object is used to easily convert [[NodeInput]]s and
    * corresponding [[Node]]s into each other.
    * @param conversionToOsm
    *   conversion [[NodeInput]] -> [[Node]]
    * @param conversionToPSDM
    *   conversion [[Node]] -> [[NodeInput]]
    */
  final case class NodeConversion(
      conversionToOsm: Map[NodeInput, Node],
      conversionToPSDM: Map[Node, NodeInput]
  ) {

    /** Returns all [[NodeInput]]s.
      */
    def allPsdmNodes: List[NodeInput] = conversionToPSDM.values.toList

    /** Returns all [[Node]]s.
      */
    def allOsmNodes: List[Node] = conversionToOsm.values.toList

    /** Converts a given [[NodeInput]] into a [[Node]].
      * @param node
      *   given psdm node
      * @return
      *   a osm node
      */
    def getOsmNode(node: NodeInput): Node = {
      conversionToOsm(node)
    }

    /** Converts multiple given [[NodeInput]]s into corresponding [[Node]]s.
      * @param nodes
      *   list of psdm nodes
      * @return
      *   list of osm nodes
      */
    def getOsmNodes(nodes: List[NodeInput]): List[Node] = {
      nodes.map { node => conversionToOsm(node) }
    }

    /** Converts a given [[Node]] into a [[NodeInput]].
      *
      * @param node
      *   given osm node
      * @return
      *   a psdm node
      */
    def getPSDMNode(node: Node): NodeInput = {
      conversionToPSDM(node)
    }

    /** Converts multiple given [[Node]]s into corresponding [[NodeInput]]s.
      *
      * @param nodes
      *   list of osm nodes
      * @return
      *   list of psdm nodes
      */
    def getPSDMNodes(nodes: List[Node]): List[NodeInput] = {
      nodes.map { node => conversionToPSDM(node) }
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

    // uses haversine to calculate distances

    /** Converts a list of nodes into a list of unique [[Connection]]s. To
      * calculate the distance of a connection, the haversine formula is used.
      * @param nodes
      *   given list of nodes
      * @return
      *   a list of unique connections
      */
    def getAllUniqueConnections(nodes: List[Node]): List[Connection] = {
      getAllUniqueCombinations(nodes)
        .map { case (nodeA, nodeB) =>
          val distance = GeoUtils.calcHaversine(
            nodeA.coordinate.getCoordinate,
            nodeB.coordinate.getCoordinate
          )
          Connection(nodeA, nodeB, distance, None)
        }
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
