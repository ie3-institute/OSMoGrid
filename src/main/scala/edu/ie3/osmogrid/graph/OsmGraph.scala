/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.graph

import edu.ie3.datamodel.graph.DistanceWeightedEdge
import edu.ie3.osmogrid.exception.GraphCopyException
import edu.ie3.util.geo.GeoUtils
import edu.ie3.util.osm.model.OsmEntity.Node
import org.jgrapht.graph.SimpleWeightedGraph
import org.jgrapht.util.SupplierUtil
import org.locationtech.jts.geom.Polygon
import tech.units.indriya.ComparableQuantity
import tech.units.indriya.quantity.Quantities
import tech.units.indriya.unit.Units
import tech.units.indriya.unit.Units.METRE
import utils.Connections.Connection
import utils.GraphUtils.{getLineSegmentBetweenNodes, hasIntersection}

import java.util
import java.util.function.Supplier
import javax.measure.Quantity
import javax.measure.quantity.Length
import scala.jdk.CollectionConverters._

@SerialVersionUID(-2797654003980753341L)
class OsmGraph(
    vertexSupplier: Supplier[Node],
    edgeSupplier: Supplier[DistanceWeightedEdge],
) extends SimpleWeightedGraph[Node, DistanceWeightedEdge](
      vertexSupplier,
      edgeSupplier,
    ) {

  def this() = {
    this(null, SupplierUtil.createSupplier(classOf[DistanceWeightedEdge]))
  }

  def addWeightedEdge(nodeA: Node, nodeB: Node): Unit = {
    val weight = GeoUtils.calcHaversine(
      nodeA.latitude,
      nodeA.longitude,
      nodeB.latitude,
      nodeB.longitude,
    )
    val edge = new DistanceWeightedEdge()
    this.setEdgeWeight(edge, weight)
    this.addEdge(nodeA, nodeB, edge)
  }

  def addWeightedEdge(
      nodeA: Node,
      nodeB: Node,
      weigth: ComparableQuantity[Length],
  ): Unit = {
    val edge = new DistanceWeightedEdge()
    this.setEdgeWeight(edge, weigth)
    this.addEdge(nodeA, nodeB, edge)
  }

  /** Method for adding the information of a [[Connection]] to this graph.
    * @param connection
    *   to be added
    */
  def addConnection(connection: Connection[Node]): Unit =
    addWeightedEdge(connection.nodeA, connection.nodeB, connection.distance)

  def setEdgeWeight(
      edge: DistanceWeightedEdge,
      weight: ComparableQuantity[Length],
  ): Unit = {
    val weightDouble: Double =
      weight.to(METRE).getValue.doubleValue
    super.setEdgeWeight(edge, weightDouble)
  }

  def getSortedEdges(node: Node): Set[DistanceWeightedEdge] = {
    edgesOf(node).asScala.toList
      .sortBy(e => e.getDistance.getValue.doubleValue())
      .reverse
      .toSet
  }

  def reconnectNodes(
      common: Node,
      connection: Connection[Node],
      doubleEdges: List[DistanceWeightedEdge],
  ): List[DistanceWeightedEdge] = {
    val edgeA = removeEdge(common, connection.nodeA)
    val edgeB = removeEdge(common, connection.nodeB)

    addWeightedEdge(
      connection.nodeA,
      connection.nodeB,
      connection.distance,
    )

    // the graph will not save two identical edges, therefore we need to re-add one edge of the double edge
    if (doubleEdges.contains(edgeA)) {
      addEdge(common, connection.nodeA, edgeA)
    }
    if (doubleEdges.contains(edgeB)) {
      addEdge(common, connection.nodeB, edgeB)
    }

    doubleEdges.diff(List(edgeA, edgeB))
  }

  /** Returns a new [[OsmGraph]] that is identical to this graph.
    */
  def copy(): OsmGraph = {
    clone() match {
      case graph: OsmGraph => graph
      case _ =>
        throw GraphCopyException()
    }
  }

  /** Returns the other node of an edge.
    *
    * @param source
    *   the source node
    * @param edge
    *   the considered edge
    * @return
    *   the node on the other side of the edge
    */
  def getOtherEdgeNode(
      source: Node,
      edge: DistanceWeightedEdge,
  ): Node = {
    if (getEdgeSource(edge) == source) getEdgeTarget(edge)
    else getEdgeSource(edge)
  }

  /** Returns true if at least two edges of this graph intersects each other.
    */
  def containsEdgeIntersection(): Boolean = {
    val edges = edgeSet().asScala

    val connections: util.List[(DistanceWeightedEdge, DistanceWeightedEdge)] =
      new util.ArrayList[(DistanceWeightedEdge, DistanceWeightedEdge)]

    // algorithm to check if two edges intersects each other
    edges.foreach(edgeA => {
      val sourceA = getEdgeSource(edgeA)
      val targetA = getEdgeTarget(edgeA)

      edges.foreach(edgeB => {
        // an edge cannot intersect itself, therefore these combination are not tested
        if (edgeA != edgeB) {
          // two combinations possible
          val t1 = (edgeA, edgeB)
          val t2 = (edgeB, edgeA)

          // check if the possible combinations are already tested
          if (!connections.contains(t1) && !connections.contains(t2)) {
            connections.add(t1)

            val sourceB = getEdgeSource(edgeB)
            val targetB = getEdgeTarget(edgeB)

            val lineA =
              getLineSegmentBetweenNodes(sourceA, targetA)
            val lineB =
              getLineSegmentBetweenNodes(sourceB, targetB)

            // checks if the two line intersects each other
            val intersection = hasIntersection(lineA, lineB)

            if (intersection) {
              return true
            }
          }
        }
      })
    })

    false
  }

  /** Returns true if at least one vertex of this graph is connected to more
    * than two edges.
    */
  def tooManyVertexConnections(): Boolean = {
    vertexSet().asScala.foreach { v =>
      if (edgesOf(v).size() > 2) {
        return true
      }
    }
    false
  }

  /** Uses the given [[Polygon]] to create a subgraph that only contains
    * vertexes and edges that are fully inside the polygon.
    * @param polygon
    *   that is used
    * @return
    *   a new [[OsmGraph]]
    */
  def subgraph(polygon: Polygon): OsmGraph = {
    // filtering out all vertexes than are not inside the polygon
    val vertexes: Set[Node] = vertexSet().asScala
      .filter(vertex =>
        polygon.covers(GeoUtils.buildPoint(vertex.latitude, vertex.longitude))
      )
      .toSet

    // filtering out all edges whose vertexes are not both inside the polygon
    val edges: Set[DistanceWeightedEdge] = edgeSet().asScala
      .filter(edge =>
        vertexes.contains(getEdgeSource(edge)) && vertexes.contains(
          getEdgeTarget(edge)
        )
      )
      .toSet

    // creates a new graph that contain only the vertexes and edges inside the polygon
    val subgraph: OsmGraph = new OsmGraph()
    vertexes.foreach(v => subgraph.addVertex(v))
    edges.foreach { edge =>
      subgraph.addWeightedEdge(
        getEdgeSource(edge),
        getEdgeTarget(edge),
        Quantities.getQuantity(edge.getDistance.getValue, Units.METRE),
      )
    }
    subgraph
  }

  /** Returns the sum of all edge weights in this graph.
    */
  def calcTotalWeight(): Double = {
    val option: Option[Quantity[Length]] = edgeSet().asScala
      .map { edge => edge.getDistance }
      .reduceOption { (a, b) => a.add(b) }

    option match {
      case Some(value) => value.getValue.doubleValue();
      case None        => 0d
    }
  }
}
