/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.model

import edu.ie3.osmogrid.exception.TestPreparationFailedException
import edu.ie3.osmogrid.model.RichClosedWay.RichClosedWay
import edu.ie3.test.common.UnitSpec
import edu.ie3.util.osm.OsmEntities
import edu.ie3.util.osm.OsmEntities.{ClosedWay, Node}
import org.locationtech.jts.geom.impl.CoordinateArraySequence
import org.locationtech.jts.geom.{GeometryFactory, Point, PrecisionModel}

import java.time.ZonedDateTime
import java.util.UUID
import scala.util.{Failure, Success, Try}

class RichClosedWaySpec extends UnitSpec {
  "Closed way with enhances functionality" when {
    "calculating the center coordinate" should {
      "provide correct values for a rectangle" in {
        RichClosedWaySpec.buildClosedWay(
          List((0, 0), (0, 5), (3, 5), (3, 0))
        ) match {
          case Success(rectangle) =>
            rectangle.center match {
              case Coordinate(lat, lon) =>
                lat shouldBe 1.5 +- 1e-3
                lon shouldBe 2.5 +- 1e-3
            }
          case Failure(exception) => fail("Test preparation failed.", exception)
        }
      }

      "provide correct values for a triangle" in {
        RichClosedWaySpec.buildClosedWay(
          List((0, 0), (0, 5), (3, 2.5))
        ) match {
          case Success(rectangle) =>
            rectangle.center match {
              case Coordinate(lat, lon) =>
                lat shouldBe 1.0 +- 1e-3
                lon shouldBe 2.5 +- 1e-3
            }
          case Failure(exception) => fail("Test preparation failed.", exception)
        }
      }

      "provide correct values for a ditched rectangle" in {
        RichClosedWaySpec.buildClosedWay(
          List((0, 0), (0, 5), (2, 2.5), (3, 5), (3, 0))
        ) match {
          case Success(rectangle) =>
            rectangle.center match {
              case Coordinate(lat, lon) =>
                lat shouldBe 1.6 +- 1e-3
                lon shouldBe 2.5 +- 1e-3
            }
          case Failure(exception) => fail("Test preparation failed.", exception)
        }
      }
    }
  }
}

object RichClosedWaySpec {
  val geomFactory =
    new GeometryFactory(new PrecisionModel(PrecisionModel.FIXED))
  def buildClosedWay(coordinates: List[(Double, Double)]): Try[ClosedWay] = {
    val nodes = coordinates.map { case (y, x) =>
      Node(
        UUID.randomUUID(),
        0,
        ZonedDateTime.now(),
        Map.empty[String, String],
        new Point(
          new CoordinateArraySequence(
            Array(new org.locationtech.jts.geom.Coordinate(x, y))
          ),
          geomFactory
        )
      )
    }
    /* Replicate first node (mandatory for a closed way) */
    nodes.headOption match {
      case Some(head) =>
        Success(
          ClosedWay(
            UUID.randomUUID(),
            0,
            ZonedDateTime.now(),
            Map.empty[String, String],
            nodes.appended(head)
          )
        )
      case None =>
        Failure(
          TestPreparationFailedException("Unable to close nodes for this way.")
        )
    }
  }
}
