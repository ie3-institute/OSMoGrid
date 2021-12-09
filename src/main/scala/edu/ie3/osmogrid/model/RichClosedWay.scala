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
import org.locationtech.jts.geom.Coordinates
import tech.units.indriya.ComparableQuantity
import tech.units.indriya.quantity.Quantities
import tech.units.indriya.unit.Units

import javax.measure.quantity.Area
import scala.util.{Failure, Success, Try}

object RichClosedWay {

  implicit class RichClosedWay(way: ClosedWay) {

    /** Calculates the area, a closed way is covering with the help of Gauss'
      * shoelace algorithm. To achieve an area in m², the geographic locations
      * are transformed into distances to minimum lat and lon of the given
      * coordinate.
      *
      * @return
      *   The area of the polygon
      * @see
      *   https://en.wikipedia.org/wiki/Shoelace_formula
      */
    def area: Try[ComparableQuantity[Area]] = {
      determineMinLatLon(way.nodes) match {
        case (Some(latMin), Some(lonMin)) =>
          val coordinates = way.nodes
            .map(_.coordinates.toCoordinate)
            .map(transformCoordinate(_, latMin, lonMin))
          Success(areaOfPolygon(coordinates))
        case _ =>
          Failure(
            IllegalCalculationException(
              "Unable to determine minimum latitude and longitude of the given way. Therefore, cannot calculate the covered area."
            )
          )
      }
    }

    /** Determine minimum latitude and longitude among the given nodes
      *
      * @param nodes
      *   nodes to assess
      * @return
      *   Options onto the minimum values
      */
    private def determineMinLatLon(nodes: List[Node]) = nodes
      .map(_.coordinates.toCoordinate)
      .map { case Coordinate(lat, lon) => (lat, lon) }
      .unzip match {
      case (lats, lons) => (lats.minOption, lons.minOption)
    }

    /** Transform the given node from angle description (as of geographical
      * position) into orthogonal distances from the origin defined by
      * [[latMin]] and [[lonMin]]
      *
      * @param coordinate
      *   The coordinate to transform
      * @param originLat
      *   Latitude of origin
      * @param originLon
      *   Longitude of origin
      * @return
      *   Coordinate as cartesian description relative to origin
      */
    private def transformCoordinate(
        coordinate: Coordinate,
        originLat: Double,
        originLon: Double
    ) = {
      val x = GeoUtils
        .calcHaversine(originLat, originLon, originLat, coordinate.lon)
        .to(Units.METRE)
        .getValue
        .doubleValue()
      val y = GeoUtils
        .calcHaversine(originLat, originLon, coordinate.lat, originLon)
        .to(Units.METRE)
        .getValue
        .doubleValue()
      Coordinate(y, x)
    }

    /** Try to calculate the area of an irregular polygon described by its
      * coordinates
      *
      * @param coordinates
      *   Coordinates describing the polygon
      * @return
      *   A try on the area
      */
    private def areaOfPolygon(
        coordinates: List[Coordinate]
    ): ComparableQuantity[Area] = {
      val rotatedCoordinates = rotate(coordinates, 1)
      val area = coordinates.zip(rotatedCoordinates).foldLeft(0.0) {
        case (
              currentArea,
              (Coordinate(yLeft, xLeft), Coordinate(yRight, xRight))
            ) =>
          val partialArea = xLeft * yRight - xRight * yLeft
          currentArea + partialArea
      } / 2
      Quantities.getQuantity(area, Units.SQUARE_METRE)
    }

    /** Rotate a list by the given amount of positions
      */
    def rotate[A]: (List[A], Int) => List[A] =
      (list: List[A], positions: Int) => {
        val shift = positions % list.length
        val (head, tail) =
          if (shift >= 0) list.splitAt(shift)
          else list.splitAt(list.length + shift)

        tail.appendedAll(head)
      }

    def center: Coordinate = way.nodes
      .map(_.coordinates.toCoordinate)
      .map { case Coordinate(lat, lon) => (lat, lon) }
      .unzip match {
      case (lats, lons) => Coordinate(mean(lats), mean(lons))
    }
  }
}
