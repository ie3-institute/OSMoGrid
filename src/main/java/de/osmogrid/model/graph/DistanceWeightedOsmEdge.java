/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
*/
package de.osmogrid.model.graph;

import static tech.units.indriya.unit.Units.METRE;

import javax.measure.Quantity;
import javax.measure.Unit;
import javax.measure.quantity.Length;
import org.jgrapht.graph.DefaultWeightedEdge;
import tech.units.indriya.quantity.Quantities;

/**
 * //ToDo: Class Description
 *
 * @version 0.1
 * @since 17.07.20
 */
public class DistanceWeightedOsmEdge extends DefaultWeightedEdge {
  private static final long serialVersionUID = -3331046813188425728L;

  protected static final Unit<Length> DEFAULT_DISTANCE_UNIT = METRE;

  public Quantity<Length> getDistance() {
    return Quantities.getQuantity(getWeight(), DEFAULT_DISTANCE_UNIT);
  }

  @Override
  public String toString() {
    return "DistanceWeightedEdge{" + "distance=" + getDistance() + "} " + super.toString();
  }
}
