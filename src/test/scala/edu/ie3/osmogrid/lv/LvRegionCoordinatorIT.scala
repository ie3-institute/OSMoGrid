/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import akka.actor.testkit.typed.Effect.SpawnedAnonymous
import akka.actor.testkit.typed.scaladsl.{
  ActorTestKit,
  BehaviorTestKit,
  ScalaTestWithActorTestKit,
  TestProbe
}
import edu.ie3.osmogrid.cfg.{OsmoGridConfig, OsmoGridConfigFactory}
import edu.ie3.osmogrid.io.input.{BoundaryAdminLevel, InputDataProvider}
import edu.ie3.osmogrid.model.OsmoGridModel.LvOsmoGridModel
import edu.ie3.osmogrid.model.SourceFilter.LvFilter
import edu.ie3.test.common.UnitSpec
import org.locationtech.jts.geom.Polygon
import org.scalatest.BeforeAndAfterAll

import java.nio.file.Paths
import scala.collection.parallel.{ParSeq, ParSet}
import scala.concurrent.duration.DurationInt
import scala.collection.parallel.CollectionConverters._
import scala.io.Source
import scala.language.postfixOps
import scala.util.Using

class LvRegionCoordinatorIT
    extends ScalaTestWithActorTestKit
    with UnitSpec
    with BeforeAndAfterAll {
  private val actorTestKit = ActorTestKit("LvRegionCoordinatorIT")

  private val (lvConfig, osmoGridModel) = readOsmModel()

  // polygons can be manually validated e.g. in QGIS
  "Creating boundary polygons from osm data" when {
    "having proper input data" should {
      val buildBoundaryPolygons =
        PrivateMethod[ParSeq[Polygon]](Symbol("buildBoundaryPolygons"))

      "result in correct polygons on county level" in {
        val polygons = getFileLines("DoBoCas_boundaries_level6.csv")

        val actualPolygons =
          (LvRegionCoordinator invokePrivate buildBoundaryPolygons(
            osmoGridModel,
            BoundaryAdminLevel.CountyLevel
          )).map(_.toString).toSet

        actualPolygons.size shouldBe 3
        actualPolygons.subsetOf(polygons) shouldBe true
      }

      "result in correct polygons on municipality level" in {
        val polygons = getFileLines("DoBoCas_boundaries_level8.csv")

        val actualPolygons =
          (LvRegionCoordinator invokePrivate buildBoundaryPolygons(
            osmoGridModel,
            BoundaryAdminLevel.MunicipalityLevel
          )).map(_.toString).toSet

        actualPolygons.size shouldBe 1
        actualPolygons.subsetOf(polygons) shouldBe true
      }

      "result in correct polygons on suburb level 1" in {
        val polygons = getFileLines("DoBoCas_boundaries_level9.csv")

        val actualPolygons =
          (LvRegionCoordinator invokePrivate buildBoundaryPolygons(
            osmoGridModel,
            BoundaryAdminLevel.Suburb1Level
          )).map(_.toString).toSet

        actualPolygons.size shouldBe 2
        actualPolygons.subsetOf(polygons) shouldBe true
      }
    }
  }

  "Partitioning osm data" when {
    "having more iterations to go" should {
      "start another partition task" in {
        val adminLevel = BoundaryAdminLevel.CountyLevel
        val replyTo = TestProbe[LvRegionCoordinator.Response]()

        val testKit = BehaviorTestKit(
          LvRegionCoordinator()
        )

        testKit.run(
          LvRegionCoordinator.Partition(
            osmoGridModel = osmoGridModel,
            administrativeLevel = adminLevel,
            lvConfig = lvConfig,
            replyTo = replyTo.ref
          )
        )

        val messages = Range(0, 3).map { _ =>
          val spawned = testKit
            .expectEffectType[SpawnedAnonymous[LvRegionCoordinator.Request]]
          val spawnedInbox = testKit.childInbox(spawned.ref)

          spawnedInbox.receiveMessage() match {
            case partitionMsg: LvRegionCoordinator.Partition =>
              partitionMsg
            case other => fail(s"Received unexpected message $other")
          }
        }
        val models = messages.map(_.osmoGridModel)

        messages.foreach(
          _.administrativeLevel shouldBe BoundaryAdminLevel.AmtLevel
        )
        messages.foreach(_.lvConfig shouldBe lvConfig)
        messages.foreach(_.replyTo shouldBe replyTo.ref)

        // Recklinghausen
        models.exists { m =>
          m.buildings.size == 88 &&
          m.highways.size == 17 &&
          m.landuses.size == 8 &&
          m.boundaries.map(_.entity.id).toSet.seq.equals(Set(62770, 56664)) &&
          m.existingSubstations.size == 0
        } shouldBe true

        // Dortmund
        models.exists { m =>
          m.buildings.size == 318 &&
          m.highways.size == 127 &&
          m.landuses.size == 20 &&
          m.boundaries
            .map(_.entity.id)
            .toSet
            .seq
            .equals(Set(10035847, 1829065)) &&
          m.existingSubstations.size == 2
        } shouldBe true

        // Bochum
        models.exists { m =>
          m.buildings.size == 24 &&
          m.highways.size == 20 &&
          m.landuses.size == 13 &&
          m.boundaries.map(_.entity.id).toSet.seq.equals(Set(1647366, 62644)) &&
          m.existingSubstations.size == 0
        } shouldBe true
      }
    }

    "having reached the last administrative level" should {
      "start MunicipalityCoordinators with the results" in {
        val adminLevel = BoundaryAdminLevel.CountyLevel
        val replyTo = TestProbe[LvRegionCoordinator.Response]()

        val testKit = BehaviorTestKit(
          LvRegionCoordinator()
        )

        val lvConfigCapped = lvConfig.copy(
          boundaryAdminLevel =
            lvConfig.boundaryAdminLevel.copy(lowest = adminLevel.osmLevel)
        )

        testKit.run(
          LvRegionCoordinator.Partition(
            osmoGridModel = osmoGridModel,
            administrativeLevel = adminLevel,
            lvConfig = lvConfigCapped,
            replyTo = replyTo.ref
          )
        )

        Range(0, 3).foreach { _ =>
          testKit
            .expectEffectType[SpawnedAnonymous[MunicipalityCoordinator.Request]]
        }
      }
    }
  }

  private def readOsmModel()
      : (OsmoGridConfig.Generation.Lv, LvOsmoGridModel) = {
    val inputResource = getClass.getResource("DoBoCas.pbf")
    assert(inputResource != null)
    val resourcePath =
      Paths
        .get(inputResource.toURI)
        .toAbsolutePath
        .toString

    val cfg = OsmoGridConfigFactory
      .parseWithoutFallback(
        s"""input.osm.pbf.file = "${resourcePath.replace("\\", "\\\\")}"
         |input.asset.file.directory = "asset_input_dir"
         |input.asset.file.hierarchic = false
         |output.csv.directory = "output_file_path"
         |generation.lv.distinctHouseConnections = true""".stripMargin
      )
      .success
      .value

    val inputActor = actorTestKit.spawn(
      InputDataProvider(cfg.input)
    )

    val inputReply = TestProbe[InputDataProvider.Response]()

    inputActor ! InputDataProvider.ReqOsm(
      inputReply.ref,
      filter = LvFilter()
    )

    inputReply
      .expectMessageType[InputDataProvider.RepOsm](30 seconds)
      .osmModel match {
      case lvModel: LvOsmoGridModel => (cfg.generation.lv.value, lvModel)
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
