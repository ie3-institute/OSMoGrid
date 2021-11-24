/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package de.osmogrid.util.quantities;

import tech.units.indriya.ComparableQuantity;

/**
 * The power density of households loads. That is, the installed capacity of a household derived
 * from its surface area. This interface should not be confused with {@link
 * edu.ie3.util.quantities.interfaces.Irradiance} which is the radiance received by a surface but
 * with the same SI units (watt per square metre).
 */
public interface PowerDensity extends ComparableQuantity<PowerDensity> {}
