/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.util.geo

import edu.ie3.osmogrid.model.Coordinate
import edu.ie3.util.CollectionUtils.RichList
import tech.units.indriya.ComparableQuantity
import tech.units.indriya.quantity.Quantities
import tech.units.indriya.unit.Units

import javax.measure.quantity.Area

/** Collection of useful routines for handling geometric and geographic things.
  * TODO: Consolidate with [[GeoUtils]] in power system utils package
  */
object GeoUtilScala {

  /** Try to calculate the area of an irregular polygon described by its
    * coordinates
    *
    * @param coordinates
    *   Coordinates describing the polygon
    * @return
    *   A try on the area
    */
  def enclosedArea(
      coordinates: List[Coordinate]
  ): Double = {
    val rotatedCoordinates = coordinates.rotate(-1)
    coordinates.zip(rotatedCoordinates).foldLeft(0.0) {
      case (
            currentArea,
            (Coordinate(yLeft, xLeft), Coordinate(yRight, xRight))
          ) =>
        val partialArea = xLeft * yRight - xRight * yLeft
        currentArea + partialArea
    } / 2
  }
}
