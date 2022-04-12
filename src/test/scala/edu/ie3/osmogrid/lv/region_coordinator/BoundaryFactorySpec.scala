/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv.region_coordinator

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import edu.ie3.osmogrid.io.input.BoundaryAdminLevel
import edu.ie3.test.common.UnitSpec

import scala.collection.parallel.ParSet
import scala.io.Source
import scala.util.Using
import scala.collection.parallel.CollectionConverters.*

class BoundaryFactorySpec extends UnitSpec with LvRegionCoordinatorTestModel {

  private val actorTestKit = ActorTestKit("LvRegionCoordinatorIT")

  private val (_, osmoGridModel) = readOsmModel(actorTestKit)

  // polygons can be manually validated e.g. in QGIS
  "Creating boundary polygons from osm data" when {
    "having proper input data" should {
      "result in correct polygons on county level" in {
        val polygons = getFileLines("DoBoCas_boundaries_level6.csv")

        val actualPolygons =
          BoundaryFactory
            .buildBoundaryPolygons(
              osmoGridModel,
              BoundaryAdminLevel.CountyLevel
            )
            .values
            .map(_.toString)
            .toSet

        actualPolygons.size shouldBe 3
        actualPolygons.subsetOf(polygons) shouldBe true
      }

      "result in correct polygons on municipality level" in {
        val polygons = getFileLines("DoBoCas_boundaries_level8.csv")

        val actualPolygons =
          BoundaryFactory
            .buildBoundaryPolygons(
              osmoGridModel,
              BoundaryAdminLevel.MunicipalityLevel
            )
            .values
            .map(_.toString)
            .toSet

        actualPolygons.size shouldBe 1
        actualPolygons.subsetOf(polygons) shouldBe true
      }

      "result in correct polygons on suburb level 1" in {
        val polygons = getFileLines("DoBoCas_boundaries_level9.csv")

        val actualPolygons =
          BoundaryFactory
            .buildBoundaryPolygons(
              osmoGridModel,
              BoundaryAdminLevel.Suburb1Level
            )
            .values
            .map(_.toString)
            .toSet

        actualPolygons.size shouldBe 2
        actualPolygons.subsetOf(polygons) shouldBe true
      }
    }
  }

  private def getFileLines(resource: String): ParSet[String] = {
    val file = getClass.getResource(resource)
    assert(file != null)
    Using(Source.fromFile(file.toURI)) { source =>
      source.getLines().toSet.par
    }.get
  }
}
