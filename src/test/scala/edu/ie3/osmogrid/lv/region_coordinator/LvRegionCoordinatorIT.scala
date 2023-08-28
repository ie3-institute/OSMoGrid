/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv.region_coordinator

import akka.actor.testkit.typed.Effect.SpawnedAnonymous
import akka.actor.testkit.typed.scaladsl.{
  BehaviorTestKit,
  ScalaTestWithActorTestKit,
  TestProbe
}
import edu.ie3.osmogrid.io.input.BoundaryAdminLevel
import edu.ie3.osmogrid.lv.{LvGridGenerator, MunicipalityCoordinator}
import edu.ie3.test.common.UnitSpec
import org.scalatest.BeforeAndAfterAll

class LvRegionCoordinatorIT
    extends ScalaTestWithActorTestKit
    with UnitSpec
    with BeforeAndAfterAll {

  private val osmoGridModel = LvTestModel.osmoGridModel
  private val lvConfig = LvTestModel.lvConfig
  private val assetInformation = LvTestModel.assetInformation

  "Partitioning osm data" when {
    "having more iterations to go" should {
      "start another partition task" in {
        val adminLevel = BoundaryAdminLevel.COUNTY_LEVEL
        val regionCoordinatorReply = TestProbe[LvRegionCoordinator.Response]()
        val gridGeneratorReply = TestProbe[LvGridGenerator.Response]

        val testKit = BehaviorTestKit(
          LvRegionCoordinator()
        )

        testKit.run(
          LvRegionCoordinator.Partition(
            osmoGridModel = osmoGridModel,
            administrativeLevel = adminLevel,
            lvConfig = lvConfig,
            lvCoordinatorGridGeneratorAdapter = gridGeneratorReply.ref,
            lvCoordinatorRegionCoordinatorAdapter = regionCoordinatorReply.ref,
            assetInformation = assetInformation
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
          _.administrativeLevel shouldBe BoundaryAdminLevel.AMT_LEVEL
        )
        messages.foreach(_.lvConfig shouldBe lvConfig)
        messages.foreach(
          _.lvCoordinatorRegionCoordinatorAdapter shouldBe regionCoordinatorReply.ref
        )
        messages.foreach(
          _.lvCoordinatorGridGeneratorAdapter shouldBe gridGeneratorReply.ref
        )

        // Recklinghausen
        models.exists { m =>
          m.buildings.size == 88 &&
          m.highways.size == 19 &&
          m.landuses.size == 17 &&
          m.boundaries.map(_.entity.id).toSet.seq.equals(Set(62770, 56664)) &&
          m.existingSubstations.isEmpty
        } shouldBe true

        // Dortmund
        models.exists { m =>
          m.buildings.size == 318 &&
          m.highways.size == 128 &&
          m.landuses.size == 27 &&
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
          m.highways.size == 24 &&
          m.landuses.size == 22 &&
          m.boundaries.map(_.entity.id).toSet.seq.equals(Set(1647366, 62644)) &&
          m.existingSubstations.isEmpty
        } shouldBe true
      }
    }

    "having reached the last administrative level" should {
      "start LvGridGenerators with the results" in {
        val adminLevel = BoundaryAdminLevel.COUNTY_LEVEL
        val lvCoordinatorRegionCoordinatorAdapter =
          TestProbe[LvRegionCoordinator.Response]()
        val lvCoordinatorGridGeneratorAdapter =
          TestProbe[LvGridGenerator.Response]()

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
            lvCoordinatorGridGeneratorAdapter =
              lvCoordinatorGridGeneratorAdapter.ref,
            lvCoordinatorRegionCoordinatorAdapter =
              lvCoordinatorRegionCoordinatorAdapter.ref,
            assetInformation = assetInformation
          )
        )

        Range(0, 3).foreach { _ =>
          testKit
            .expectEffectType[SpawnedAnonymous[LvGridGenerator.Request]]
        }
      }
    }
  }

}
