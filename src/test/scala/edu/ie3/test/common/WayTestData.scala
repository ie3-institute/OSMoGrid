/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.test.common

import edu.ie3.osmogrid.model.Coordinate
import edu.ie3.osmogrid.exception.TestPreparationFailedException
import edu.ie3.util.osm.OsmEntities.{ClosedWay, Node}
import org.locationtech.jts.geom
import org.locationtech.jts.geom.impl.CoordinateArraySequence
import org.locationtech.jts.geom.{GeometryFactory, Point, PrecisionModel}
import tech.units.indriya.ComparableQuantity
import tech.units.indriya.quantity.Quantities
import tech.units.indriya.unit.Units

import java.time.{ZoneId, ZonedDateTime}
import java.util.UUID
import javax.measure.quantity.Area

trait WayTestData {

  /** This it TU Dortmund's BCI-G2 building as of 12/9/2021. The area has been
    * calculated with JOSM utilizing the measurement plugin
    *
    * @see
    *   https://www.openstreetmap.org/way/24710099
    */
  object G2 {
    private val coordinates = {
      val tempCoordinates = Vector(
        Coordinate(
          7.41203840000,
          51.49282570000
        ),
        Coordinate(
          7.41206190000,
          51.49275640000
        ),
        Coordinate(
          7.41207890000,
          51.49275870000
        ),
        Coordinate(
          7.41208990000,
          51.49272690000
        ),
        Coordinate(
          7.41203640000,
          51.49271980000
        ),
        Coordinate(
          7.41203680000,
          51.49271890000
        ),
        Coordinate(
          7.41199410000,
          51.49271330000
        ),
        Coordinate(
          7.41199780000,
          51.49270280000
        ),
        Coordinate(
          7.41200200000,
          51.49269070000
        ),
        Coordinate(
          7.41204420000,
          51.49269620000
        ),
        Coordinate(
          7.41204490000,
          51.49269540000
        ),
        Coordinate(
          7.41209800000,
          51.49270240000
        ),
        Coordinate(
          7.41211210000,
          51.49266100000
        ),
        Coordinate(
          7.41216780000,
          51.49266840000
        ),
        Coordinate(
          7.41218810000,
          51.49260880000
        ),
        Coordinate(
          7.41239910000,
          51.49263660000
        ),
        Coordinate(
          7.41236470000,
          51.49273880000
        ),
        Coordinate(
          7.41235700000,
          51.49276140000
        ),
        Coordinate(
          7.41232250000,
          51.49286340000
        ),
        Coordinate(
          7.41225940000,
          51.49285500000
        ),
        Coordinate(
          7.41222390000,
          51.49285030000
        )
      )
      tempCoordinates.headOption match {
        case Some(headCoordinate) => tempCoordinates.appended(headCoordinate)
        case None =>
          throw TestPreparationFailedException(
            "Unable to repeat the first coordinate."
          )
      }
    }

    private val geometryFactory = new GeometryFactory(
      new PrecisionModel(PrecisionModel.FIXED)
    )
    private val lastEdited =
      ZonedDateTime.of(2019, 9, 27, 11, 48, 0, 0, ZoneId.of("UTC"))
    val building: ClosedWay = ClosedWay(
      UUID.fromString("25b7b8d3-e2dd-471d-b2a0-f45cbcaca730"),
      24710099,
      lastEdited,
      Map.empty[String, String],
      coordinates.map { case Coordinate(lat, lon) =>
        Node(
          UUID.randomUUID(),
          0,
          lastEdited,
          Map.empty[String, String],
          new Point(
            new CoordinateArraySequence(
              Array(new geom.Coordinate(lon, lat))
            ),
            geometryFactory
          )
        )
      }.toList
    )

    val buildingArea: ComparableQuantity[Area] =
      Quantities.getQuantity(487.043, Units.SQUARE_METRE)
  }
}
