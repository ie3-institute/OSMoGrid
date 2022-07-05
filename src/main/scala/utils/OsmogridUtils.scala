/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package utils

import edu.ie3.osmogrid.exception.OsmDataException
import edu.ie3.util.geo.RichGeometries.RichPolygon
import edu.ie3.util.osm.OsmUtils.GeometryUtils.buildPolygon
import edu.ie3.util.osm.model.OsmEntity.Node
import edu.ie3.util.osm.model.OsmEntity.Way.ClosedWay
import edu.ie3.util.quantities.PowerSystemUnits
import edu.ie3.util.quantities.interfaces.Irradiance
import org.locationtech.jts.geom.{Coordinate, Polygon}
import org.locationtech.jts.math.Vector2D
import tech.units.indriya.ComparableQuantity
import tech.units.indriya.quantity.Quantities
import tech.units.indriya.unit.Units

import javax.measure.Quantity
import javax.measure.quantity.{Area, Power}
import scala.collection.parallel.ParSeq
import scala.math.BigDecimal.RoundingMode
import scala.util.{Failure, Success}

object OsmogridUtils {

  /** Checks whether or not the center of a building is within a landuse
    *
    * @param buildingCenter
    *   the building center
    * @param landuses
    *   the landuse
    * @return
    *   whether or not the center of a building is within a landuse
    */
  def isInsideLanduse(
      buildingCenter: Coordinate,
      landuses: ParSeq[Polygon]
  ): Boolean = {
    for (landuse <- landuses) {
      if (landuse.containsCoordinate(buildingCenter)) return true
    }
    false
  }

  def safeBuildPolygon(
      closedWay: ClosedWay,
      nodes: Map[Long, Node]
  ): Polygon = {
    buildPolygon(closedWay, nodes) match {
      case Success(polygon) => polygon
      case Failure(exception) =>
        throw OsmDataException(
          s"Could not build polygon from closed way: $closedWay. Exception: ",
          exception
        )
    }
  }

  /** Calculates the power value of a household load based on the provided
    * building area and the provided average power density value and the
    * provided average household area size
    *
    * @param area
    *   area of the household
    * @param powerDensity
    *   average power per area
    */
  def calcHouseholdPower(
      area: ComparableQuantity[Area],
      powerDensity: ComparableQuantity[Irradiance]
  ): ComparableQuantity[Power] = {
    val power = area
      .to(Units.SQUARE_METRE)
      .multiply(powerDensity.to(PowerSystemUnits.WATT_PER_SQUAREMETRE))
      .asType(classOf[Power])
      .to(PowerSystemUnits.KILOWATT)
    round(power, 4)
  }

  @deprecated("Move to QuantityUtils of the PowerSystemUtils")
  private def round[T <: Quantity[T]](
      quantity: ComparableQuantity[T],
      decimals: Int
  ): ComparableQuantity[T] = {
    if (decimals < 0)
      throw new IllegalArgumentException(
        "You can not round to negative decimal places."
      )
    val rounded = BigDecimal
      .valueOf(quantity.getValue.doubleValue())
      .setScale(decimals, RoundingMode.HALF_UP)
      .doubleValue
    Quantities.getQuantity(rounded, quantity.getUnit)
  }

  def orthogonalProjection(
      linePtA: Coordinate,
      linePtB: Coordinate,
      pt: Coordinate
  ): Coordinate = {
    orthogonalProjection(
      Vector2D.create(linePtA),
      Vector2D.create(linePtB),
      Vector2D.create(pt)
    ).toCoordinate
  }

  /** Calculate the orthogonal projection of a point onto a line. Credits to
    * Andrey Tyukin. Check out how and why this works here:
    * https://stackoverflow.com/questions/54009832/scala-orthogonal-projection-of-a-point-onto-a-line
    *
    * @param linePtA
    *   first point of the line
    * @param linePtB
    *   second point of the line
    * @param pt
    *   the point for which to calculate the projection
    * @return
    *   the projected point
    */
  @deprecated("Move to GeoUtils of the PowerSystemUtils")
  def orthogonalProjection(
      linePtA: Vector2D,
      linePtB: Vector2D,
      pt: Vector2D
  ): Vector2D = {
    val v = pt.subtract(linePtA)
    val d = linePtB.subtract(linePtA)
    linePtA.add(d.multiply((v dot d) / d.lengthSquared()))
  }

}
