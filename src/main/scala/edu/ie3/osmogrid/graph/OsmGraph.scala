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
import tech.units.indriya.unit.Units.{METRE, getInstance}

import java.util.function.Supplier
import javax.measure.quantity.Length
import org.jgrapht.graph.SimpleWeightedGraph
import org.jgrapht.util.SupplierUtil
import tech.units.indriya.ComparableQuantity

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

  def setEdgeWeight(
      edge: DistanceWeightedEdge,
      weight: ComparableQuantity[Length]
  ): Unit = {
    val weightDouble: Double =
      weight.to(METRE).getValue.doubleValue
    super.setEdgeWeight(edge, weightDouble)
  }

  def reconnectNodes(
      common: Node,
      connection: Connection
  ): List[DistanceWeightedEdge] = {
    val edgeA = removeEdge(common, connection.nodeA)
    val edgeB = removeEdge(common, connection.nodeB)

    addWeightedEdge(
      connection.nodeA,
      connection.nodeB,
      connection.distance
    )

    List(edgeA, edgeB)
  }

  def copy(): OsmGraph = {
    clone() match {
      case graph: OsmGraph => graph
      case _ =>
        throw GraphCopyException()
    }
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
