/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package de.osmogrid.util;

import javax.measure.Quantity;
import tech.units.indriya.quantity.Quantities;

/**
 * Provides utils for quantities.
 *
 * @author Mahr
 * @since 17.12.2018
 */
public class QuantityUtils {

  /**
   * Description copied from {@link Math}: Returns the smallest (closest to negative infinity)
   * double value that is greater than or equal to the argument and is equal to a mathematical
   * integer. For more information look at description from {@link Math}.
   *
   * @param quantity A {@link Quantity} value.
   * @return The smallest (closest to negative infinity) floating-point value that is greater than
   *     or equal to the argument and is equal to a mathematical integer.
   */
  public static Quantity<?> ceil(Quantity<?> quantity) {
    double ceilVal = Math.ceil(quantity.getValue().doubleValue());
    return Quantities.getQuantity(ceilVal, quantity.getUnit());
  }

  /**
   * Description copied from {@link Math}: Returns the largest (closest to positive infinity) double
   * value that is less than or equal to the argument and is equal to a mathematical integer. For
   * more information look at description from {@link Math}.
   *
   * @param quantity A {@link Quantity} value.
   * @return The largest (closest to positive infinity) floating-point value that less than or equal
   *     to the argument and is equal to a mathematical integer.
   */
  public static Quantity<?> floor(Quantity<?> quantity) {
    double floorVal = Math.floor(quantity.getValue().doubleValue());
    return Quantities.getQuantity(floorVal, quantity.getUnit());
  }

  /**
   * Description copied from {@link Math}: Returns the closest long to the argument, with ties
   * rounding to positive infinity. For more information look at description from {@link Math}.
   *
   * @param quantity A {@link Quantity} value to be rounded.
   * @return The value of the argument rounded to the nearest long value.
   */
  public static Quantity<?> round(Quantity<?> quantity, int precision) {
    double divisor = Math.pow(0.1, precision);
    double roundVal = (Math.round(quantity.getValue().doubleValue() / divisor)) * divisor;
    return Quantities.getQuantity(roundVal, quantity.getUnit());
  }
}
