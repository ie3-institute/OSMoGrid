package edu.ie3.osmogrid.graph

import edu.ie3.datamodel.graph.DistanceWeightedEdge
import edu.ie3.util.osm.model.OsmEntity.Node

import java.util.function.Supplier
import javax.measure.Quantity
import javax.measure.quantity.Length
import org.jgrapht.graph.SimpleWeightedGraph
import org.jgrapht.util.SupplierUtil
import tech.units.indriya.ComparableQuantity


@SerialVersionUID(-2797654003980753341L)
class OsmGraph(vertexSupplier: Supplier[Node], edgeSupplier: Supplier[DistanceWeightedEdge])
  extends SimpleWeightedGraph[Node, DistanceWeightedEdge] (vertexSupplier, edgeSupplier) {

  def this() = {
    this(null, SupplierUtil.createSupplier(classOf[DistanceWeightedEdge]))
  }

  def setEdgeWeight(edge: DistanceWeightedEdge, weight: ComparableQuantity[Length]): Unit = {
    val weightDouble: Double = weight.to(DistanceWeightedEdge.DEFAULT_DISTANCE_UNIT).getValue.doubleValue
    super.setEdgeWeight(edge, weightDouble)
  }


  override final def setEdgeWeight(edge: DistanceWeightedEdge, distanceInMeters: Double): Unit = {
    super.setEdgeWeight(edge, distanceInMeters)
  }
}
