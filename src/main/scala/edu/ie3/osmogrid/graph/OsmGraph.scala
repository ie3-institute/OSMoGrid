/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.graph

import edu.ie3.datamodel.graph.DistanceWeightedEdge
import edu.ie3.osmogrid.exception.GraphCopyException
import edu.ie3.osmogrid.routingproblem.Definitions.Connection
import edu.ie3.util.geo.GeoUtils
import edu.ie3.util.osm.model.OsmEntity.Node
import tech.units.indriya.unit.Units.METRE

import java.util.function.Supplier
import javax.measure.quantity.Length
import org.jgrapht.graph.SimpleWeightedGraph
import org.jgrapht.util.SupplierUtil
import org.locationtech.jts.geom.Polygon
import tech.units.indriya.ComparableQuantity
import tech.units.indriya.quantity.Quantities
import tech.units.indriya.unit.Units

import javax.measure.Quantity
import scala.jdk.CollectionConverters._

@SerialVersionUID(-2797654003980753341L)
class OsmGraph(
    vertexSupplier: Supplier[Node],
    edgeSupplier: Supplier[DistanceWeightedEdge]
) extends SimpleWeightedGraph[Node, DistanceWeightedEdge](
      vertexSupplier,
      edgeSupplier
    ) {

  def this() = {
    this(null, SupplierUtil.createSupplier(classOf[DistanceWeightedEdge]))
  }

  def addWeightedEdge(nodeA: Node, nodeB: Node): Unit = {
    val weight = GeoUtils.calcHaversine(
      nodeA.latitude,
      nodeA.longitude,
      nodeB.latitude,
      nodeB.longitude
    )
    val edge = new DistanceWeightedEdge()
    this.setEdgeWeight(edge, weight)
    this.addEdge(nodeA, nodeB, edge)
  }

  def addWeightedEdge(
      nodeA: Node,
      nodeB: Node,
      weigth: ComparableQuantity[Length]
  ): Unit = {
    val edge = new DistanceWeightedEdge()
    this.setEdgeWeight(edge, weigth)
    this.addEdge(nodeA, nodeB, edge)
  }

  def addConnection(connection: Connection): DistanceWeightedEdge = {
    addWeightedEdge(connection.nodeA, connection.nodeB, connection.distance)
    getEdge(connection.nodeA, connection.nodeB)
  }

  def setEdgeWeight(
      edge: DistanceWeightedEdge,
      weight: ComparableQuantity[Length]
  ): Unit = {
    val weightDouble: Double =
      weight.to(METRE).getValue.doubleValue
    super.setEdgeWeight(edge, weightDouble)
  }

  def getSortedEdges(node: Node): List[DistanceWeightedEdge] = {
    edgesOf(node).asScala.toList
      .sortBy(e => e.getDistance.getValue.doubleValue())
      .reverse
  }

  def reconnectNodes(
      common: Node,
      connection: Connection,
      doubleEdges: List[DistanceWeightedEdge]
  ): List[DistanceWeightedEdge] = {
    val edgeA = removeEdge(common, connection.nodeA)
    val edgeB = removeEdge(common, connection.nodeB)

    addWeightedEdge(
      connection.nodeA,
      connection.nodeB,
      connection.distance
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

  def copy(): OsmGraph = {
    clone() match {
      case graph: OsmGraph => graph
      case _ =>
        throw GraphCopyException()
    }
  }

  def subGraph(polygon: Polygon): OsmGraph = {
    val vertexes: Set[Node] = vertexSet().asScala
      .filter(vertex =>
        polygon.contains(GeoUtils.buildPoint(vertex.latitude, vertex.longitude))
      )
      .toSet
    val edges: Set[DistanceWeightedEdge] = edgeSet().asScala
      .filter(edge =>
        vertexes.contains(getEdgeSource(edge)) || vertexes.contains(
          getEdgeTarget(edge)
        )
      )
      .toSet

    // copy of edges
    val vertexMap: Map[Node, Node] = vertexes.map { v => v -> v.copy() }.toMap

    val graph = new OsmGraph()
    vertexMap.values.foreach { v => graph.addVertex(v) }

    // adds edges with
    edges.foreach { edge =>
      val source = getEdgeSource(edge)
      val target = getEdgeTarget(edge)

      graph.addWeightedEdge(
        vertexMap(source),
        vertexMap(target),
        Quantities.getQuantity(edge.getDistance.getValue, Units.METRE)
      )
    }

    graph
  }

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
