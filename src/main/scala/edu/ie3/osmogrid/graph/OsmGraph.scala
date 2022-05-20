/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.graph

import edu.ie3.datamodel.graph.DistanceWeightedEdge
import edu.ie3.util.geo.GeoUtils
import edu.ie3.util.osm.model.OsmEntity.Node

import tech.units.indriya.unit.Units.METRE
import java.util.function.Supplier
import javax.measure.Quantity
import javax.measure.quantity.Length
import org.jgrapht.graph.SimpleWeightedGraph
import org.jgrapht.util.SupplierUtil
import tech.units.indriya.ComparableQuantity

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

  def setEdgeWeight(
      edge: DistanceWeightedEdge,
      weight: ComparableQuantity[Length]
  ): Unit = {
    val weightDouble: Double =
      weight.to(METRE).getValue.doubleValue
    super.setEdgeWeight(edge, weightDouble)
  }

  override final def setEdgeWeight(
      edge: DistanceWeightedEdge,
      distanceInMeters: Double
  ): Unit = {
    super.setEdgeWeight(edge, distanceInMeters)
  }
}
