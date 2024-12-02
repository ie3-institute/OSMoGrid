/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv.region_coordinator

import edu.ie3.osmogrid.io.input.BoundaryAdminLevel
import edu.ie3.test.common.UnitSpec

import scala.io.Source
import scala.util.Using

class BoundaryFactorySpec extends UnitSpec {

  private val osmoGridModel = LvTestModel.osmoGridModel

  // polygons can be manually validated e.g. in QGIS
  "Creating boundary polygons from osm data" when {
    "having proper input data" should {
      "result in correct polygons on county level" in {
        val polygons = getFileLines("DoBoCas_boundaries_level6.csv")

        val actualPolygons =
          BoundaryFactory
            .buildBoundaryPolygons(
              osmoGridModel,
              BoundaryAdminLevel.COUNTY_LEVEL,
            )
            .values
            .map(_.toString)
            .seq
            .toSet

        actualPolygons shouldBe polygons
      }

      "result in correct polygons on municipality level" in {
        val polygons = getFileLines("DoBoCas_boundaries_level8.csv")

        val actualPolygons =
          BoundaryFactory
            .buildBoundaryPolygons(
              osmoGridModel,
              BoundaryAdminLevel.MUNICIPALITY_LEVEL,
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
              BoundaryAdminLevel.SUBURB_1_LEVEL,
            )
            .values
            .map(_.toString)
            .seq
            .toSet

        actualPolygons shouldBe polygons
      }
    }
  }

  private def getFileLines(resource: String): Set[String] = {
    val file = getClass.getResource(resource)
    assert(file != null)
    Using(Source.fromFile(file.toURI)) { source =>
      source.getLines().toSet
    }.success.get
  }
}
