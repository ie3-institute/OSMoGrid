/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.model

import breeze.stats.mean
import edu.ie3.osmogrid.exception.IllegalCalculationException
import edu.ie3.osmogrid.model.Coordinate.RichPoint
import edu.ie3.util.geo.GeoUtils
import edu.ie3.util.osm.OsmEntities.{ClosedWay, Node}
import edu.ie3.util.CollectionUtils.RichList
import org.locationtech.jts.geom.Coordinates
import tech.units.indriya.ComparableQuantity
import tech.units.indriya.quantity.Quantities
import tech.units.indriya.unit.Units

import javax.measure.quantity.Area
import scala.util.{Failure, Success, Try}

object WayUtils {

  @deprecated("Will be part of ClosedWay in soon future.")
  implicit class RichClosedWay(way: ClosedWay) {

    /** Determine the area of the polygon.
      *
      * @return
      *   The area of the polygon
      */
    def area: Try[ComparableQuantity[Area]] = Success(
      Quantities.getQuantity(
        42d,
        Units.SQUARE_METRE
      )
    )

    def center: Coordinate = way.nodes
      .slice(
        0,
        way.nodes.length - 1
      ) // Last node has to be the same as the first one to be a closed way
      .map(_.coordinates.toCoordinate)
      .map { case Coordinate(lat, lon) => (lat, lon) }
      .unzip match {
      case (lats, lons) => Coordinate(mean(lats), mean(lons))
    }
  }
}
