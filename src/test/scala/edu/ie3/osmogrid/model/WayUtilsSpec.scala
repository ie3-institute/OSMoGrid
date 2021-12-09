/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.model

import edu.ie3.osmogrid.exception.TestPreparationFailedException
import edu.ie3.osmogrid.model.WayUtils.RichClosedWay
import edu.ie3.test.common.UnitSpec
import edu.ie3.util.osm.OsmEntities
import edu.ie3.util.osm.OsmEntities.{ClosedWay, Node}
import org.locationtech.jts.geom.impl.CoordinateArraySequence
import org.locationtech.jts.geom.{GeometryFactory, Point, PrecisionModel}

import java.time.ZonedDateTime
import java.util.UUID
import scala.util.{Failure, Success, Try}

class WayUtilsSpec extends UnitSpec {
  "Closed way with enhances functionality" when {
    "calculating the center coordinate" should {
      "provide correct values for a rectangle" in {
        WayUtilsSpec.buildClosedWay(
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
        WayUtilsSpec.buildClosedWay(
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
        WayUtilsSpec.buildClosedWay(
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

    "determining minimum latitude and longitude" should {
      val determineMinLatLon = PrivateMethod[(Option[Double], Option[Double])](
        Symbol("determineMinLatLon")
      )
      "provide nothing, if nodes are empty" in {
        val (minLat, minLon) =
          WayUtils invokePrivate determineMinLatLon(List.empty[Node])
        minLat.isDefined shouldBe false
        minLon.isDefined shouldBe false
      }

      "provide correct values" in {
        WayUtilsSpec.buildNodes(
          List((1, 0), (1, 5), (2, 2.5), (3, 5), (3, 0))
        ) match {
          case Success(nodes) =>
            println(nodes)
            WayUtils invokePrivate determineMinLatLon(nodes) match {
              case (Some(minLat), Some(minLon)) =>
                minLat shouldBe 1.0 +- 1e-3
                minLon shouldBe 0.0 +- 1e-3
              case _ =>
                fail(
                  "Received nothing as minimum coordinates, which is not expected"
                )
            }
          case Failure(exception) => fail("Node preparation failed", exception)
        }
      }
    }

    "transforming coordinates from geo to distances in metre" should {
      val (originLon, originLat) = (7.4116482, 51.4843281)
      val transformCoordinate =
        PrivateMethod[Coordinate](Symbol("transformCoordinate"))

      "have trivial lat value, if longitudes are same" in {
        WayUtils invokePrivate transformCoordinate(
          Coordinate(7.4116483, originLon),
          originLat,
          originLon
        ) match {
          case Coordinate(lat, lon) =>
            lon shouldBe 0.0 +- 1e-3
            lat shouldBe 4906148.2732 +- 1e-3
        }
      }

      "have trivial lon value, if longitudes are same" in {
        WayUtils invokePrivate transformCoordinate(
          Coordinate(originLat, 51.4843282),
          originLat,
          originLon
        ) match {
          case Coordinate(lat, lon) =>
            lon shouldBe 3008237.7976 +- 1e-3
            lat shouldBe 0.0 +- 1e-3
        }
      }
    }
  }
}

object WayUtilsSpec {
  val geomFactory =
    new GeometryFactory(new PrecisionModel(PrecisionModel.FIXED))
  def buildClosedWay(coordinates: List[(Double, Double)]): Try[ClosedWay] =
    buildNodes(coordinates).map(
      ClosedWay(
        UUID.randomUUID(),
        0,
        ZonedDateTime.now(),
        Map.empty[String, String],
        _
      )
    )

  private def buildNodes(
      coordinates: List[(Double, Double)]
  ): Try[List[Node]] = {
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
          nodes.appended(head)
        )
      case None =>
        Failure(
          TestPreparationFailedException("Unable to close nodes for this way.")
        )
    }
  }
}
