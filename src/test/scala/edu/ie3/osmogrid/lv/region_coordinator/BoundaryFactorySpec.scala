/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv.region_coordinator

import edu.ie3.osmogrid.io.input.BoundaryAdminLevel
import edu.ie3.test.common.UnitSpec

import scala.collection.parallel.ParSet
import scala.io.Source
import scala.math.BigDecimal.RoundingMode
import scala.util.Using

class BoundaryFactorySpec extends UnitSpec {

  private val osmoGridModel = LvTestModel.osmoGridModel
  private val osmoGridModelHelgoland = LvTestModel.osmoGridModelHelgoland

  "Creating boundary polygons from osm data" when {
    "having proper input data" should {
      "result in correct polygons on municipality level with multiple polygons" in {
        val rawPolygons = getFileLines("Helgoland_boundaries_level8.csv")
        val polygons = rawPolygons.map(truncate(_, 6))

        val actualPolygons: ParSet[String] =
          BoundaryFactory
            .buildBoundaryPolygons(
              osmoGridModelHelgoland,
              BoundaryAdminLevel.MUNICIPALITY_LEVEL
            )
            .values
            .flatMap(_.toList)
            .map(_.toString)
            .map(truncate(_, 6))
            .toSet

        actualPolygons.mkString(", ") shouldBe polygons.mkString(", ")
      }

      "result in correct polygons on county level" in {
        val polygons = getFileLines("DoBoCas_boundaries_level6.csv")

        val actualPolygons =
          BoundaryFactory
            .buildBoundaryPolygons(
              osmoGridModel,
              BoundaryAdminLevel.COUNTY_LEVEL
            )
            .values
            .flatMap(_.toList) // Flatten the lists of polygons
            .map(_.toString)
            .toSet

        val expectedSet = polygons.map(_.trim)
        val actualSet = actualPolygons.map(_.trim)

        actualSet.size shouldBe expectedSet.size
        actualSet.forall(expectedSet.contains) shouldBe true
      }

      "result in correct polygons on municipality level" in {
        val polygons = getFileLines("DoBoCas_boundaries_level8.csv")

        val actualPolygons =
          BoundaryFactory
            .buildBoundaryPolygons(
              osmoGridModel,
              BoundaryAdminLevel.MUNICIPALITY_LEVEL
            )
            .values
            .map(_.toString)
            .seq
            .toSet

        actualPolygons shouldBe polygons
      }

      "result in correct polygons on suburb level 1" in {
        val polygons = getFileLines("DoBoCas_boundaries_level9.csv")

        val actualPolygons =
          BoundaryFactory
            .buildBoundaryPolygons(
              osmoGridModel,
              BoundaryAdminLevel.SUBURB_1_LEVEL
            )
            .values
            .map(_.toString)
            .seq
            .toSet

        actualPolygons shouldBe polygons
      }
    }
  }

  private def truncate(polygon: String, precision: Int): String = {
    val pattern = "(-?\\d+\\.\\d+) (-?\\d+\\.\\d+)".r
    pattern.replaceAllIn(
      polygon,
      m =>
        BigDecimal(m.group(1))
          .setScale(precision, RoundingMode.DOWN)
          .toString() + " " +
          BigDecimal(m.group(2))
            .setScale(precision, RoundingMode.DOWN)
            .toString()
    )
  }

  private def getFileLines(resource: String): Set[String] = {
    val file = getClass.getResource(resource)
    assert(file != null)
    Using(Source.fromFile(file.toURI)) { source =>
      source.getLines().toSet
    }.success.get
  }
}
