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
import edu.ie3.util.quantities.QuantityUtils.RichQuantity
import edu.ie3.util.quantities.interfaces.Irradiance
import org.locationtech.jts.geom.{Coordinate, Polygon}
import tech.units.indriya.ComparableQuantity
import tech.units.indriya.unit.Units

import javax.measure.quantity.{Area, Power}
import scala.collection.parallel.ParSeq
import scala.util.{Failure, Success}

object OsmoGridUtils {

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
    power.round(4)
  }
}
