/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.graph

import edu.ie3.datamodel.graph.DistanceWeightedEdge
import edu.ie3.util.osm.model.OsmEntity.Node

import java.util.function.Supplier
import javax.measure.Quantity
import javax.measure.quantity.Length
import org.jgrapht.graph.SimpleWeightedGraph
import tech.units.indriya.ComparableQuantity


@SerialVersionUID(-2797654003980753341L)
class OsmGraph(vertexSupplier: Supplier[Node], edgeSupplier: Supplier[DistanceWeightedEdge])
  extends SimpleWeightedGraph[Node, DistanceWeightedEdge] (vertexSupplier, edgeSupplier) {

  def apply(): OsmGraph = {
    this(null, SupplierUtil.createSupplier(classOf[DistanceWeightedOsmEdge]))
  }

  def setEdgeWeight(edge: DistanceWeightedOsmEdge, weight: ComparableQuantity[Length]): Unit = {
    val weightDouble: Double = weight.to(DistanceWeightedOsmEdge.DEFAULT_DISTANCE_UNIT).getValue.doubleValue
    super.setEdgeWeight(edge, weightDouble)
  }


  override final def setEdgeWeight(edge: DistanceWeightedOsmEdge, distanceInMeters: Double): Unit = {
    super.setEdgeWeight(edge, distanceInMeters)
  }
}
