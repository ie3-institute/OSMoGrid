/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
*/
package de.osmogrid.util.quantities;

import static tech.units.indriya.unit.Units.SQUARE_METRE;
import static tech.units.indriya.unit.Units.WATT;

import edu.ie3.util.quantities.PowerSystemUnits;
import java.util.HashSet;
import javax.measure.Unit;
import tech.units.indriya.format.SimpleUnitFormat;
import tech.units.indriya.unit.ProductUnit;

public class OsmoGridUnits {

  public static final Unit<PowerDensity> WATT_PER_SQUARE_METRE =
      new ProductUnit<>(WATT.divide(SQUARE_METRE));

  public static final Unit<PowerDensity> KILOWATT_PER_SQUARE_METRE =
      new ProductUnit<>(PowerSystemUnits.KILOWATT.divide(SQUARE_METRE));

  private static final HashSet<String> REGISTERED_LABELS = new HashSet<>();

  static {
    addUnit(WATT_PER_SQUARE_METRE, "W/m²");
    addUnit(KILOWATT_PER_SQUARE_METRE, "kW/m²");
  }

  /**
   * Units must be registered via this method or they cannot be serialized/deserialized! If the
   * return-value is null, the unit was already registered
   */
  private static Unit<?> addUnit(Unit<?> unit, String label) {
    if (REGISTERED_LABELS.contains(label)) {
      return null;
    }

    SimpleUnitFormat.getInstance().label(unit, label);
    REGISTERED_LABELS.add(label);
    return unit;
  }
}
