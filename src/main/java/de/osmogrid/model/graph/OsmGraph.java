/*
 * © 2020. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
*/
package de.osmogrid.model.graph;

import edu.ie3.datamodel.graph.DistanceWeightedEdge;
import java.util.function.Supplier;
import javax.measure.Quantity;
import javax.measure.quantity.Length;
import org.jgrapht.graph.SimpleWeightedGraph;
import tec.uom.se.ComparableQuantity;

/**
 * //ToDo: Class Description
 *
 * @version 0.1
 * @since 17.07.20
 */
public class OsmGraph extends SimpleWeightedGraph<OsmGridNode, DistanceWeightedOsmEdge> {

  private static final long serialVersionUID = -2797654003980753341L;

  public OsmGraph() {
    super(DistanceWeightedOsmEdge.class);
  }

  public OsmGraph(
      Supplier<OsmGridNode> vertexSupplier, Supplier<DistanceWeightedOsmEdge> edgeSupplier) {
    super(vertexSupplier, edgeSupplier);
  }

  /**
   * Assigns a {@link Quantity} of type {@link Length} to an instance of edge {@link
   * DistanceWeightedOsmEdge}
   *
   * @param edge edge whose weight should be altered
   * @param weight the weight of the {@link DistanceWeightedOsmEdge}
   */
  public void setEdgeWeight(DistanceWeightedOsmEdge edge, ComparableQuantity<Length> weight) {
    double weightDouble =
        weight.to(DistanceWeightedOsmEdge.DEFAULT_DISTANCE_UNIT).getValue().doubleValue();
    super.setEdgeWeight(edge, weightDouble);
  }

  /**
   * The only purpose for overriding this method is to provide a better indication of the unit that
   * is expected to be passed in. It is highly advised to use the {@link
   * #setEdgeWeight(DistanceWeightedOsmEdge, ComparableQuantity)} for safety purposes that the
   * provided edge weight is correct.
   *
   * @param edge the edge whose weight should be altered
   * @param distanceInMeters the weight of the {@link DistanceWeightedEdge} in meters
   */
  @Override
  public final void setEdgeWeight(DistanceWeightedOsmEdge edge, double distanceInMeters) {
    super.setEdgeWeight(edge, distanceInMeters);
  }
}
