/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.model

import org.locationtech.jts.geom.Point

case class Coordinate(lat: Double, lon: Double)

object Coordinate {
  implicit class RichPoint(point: Point) {
    def toCoordinate: Coordinate = Coordinate(point.getY, point.getX)
  }
}
