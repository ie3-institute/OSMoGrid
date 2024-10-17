/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv.region_coordinator

import org.apache.pekko.actor.testkit.typed.Effect.SpawnedAnonymous
import org.apache.pekko.actor.testkit.typed.scaladsl.{
  BehaviorTestKit,
  ScalaTestWithActorTestKit,
  TestProbe
}
import edu.ie3.osmogrid.io.input.BoundaryAdminLevel
import edu.ie3.osmogrid.lv.{LvGridRequest, LvGridResponse}
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
        val regionCoordinatorReply = TestProbe[LvRegionResponse]()
        val gridGeneratorReply = TestProbe[LvGridResponse]()

        val testKit = BehaviorTestKit(
          LvRegionCoordinator()
        )

        testKit.run(
          Partition(
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
            .expectEffectType[SpawnedAnonymous[LvRegionRequest]]
          val spawnedInbox = testKit.childInbox(spawned.ref)

          spawnedInbox.receiveMessage() match {
            case partitionMsg: Partition =>
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

        val testCases = Seq(
          (Set(1829065, 10035847), 318, 128, 26, 2),
          (Set(62644, 1647366), 24, 25, 21, 0),
          (Set(56664, 62770), 88, 20, 17, 0)
        )

        testCases.zip(models).foreach {
          case (
                (
                  expectedBoundaryIds,
                  expectedBuildings,
                  expectedHighways,
                  expectedLanduses,
                  expectedSubstations
                ),
                model
              ) =>
            val actualBoundaryIds = model.boundaries.map(_.entity.id).toSet

            model.buildings.size shouldBe expectedBuildings
            model.highways.size shouldBe expectedHighways
            model.landuses.size shouldBe expectedLanduses
            actualBoundaryIds.mkString(", ") shouldBe expectedBoundaryIds
              .mkString(", ")
            model.existingSubstations.size shouldBe expectedSubstations
        }

      }
    }

    "having reached the last administrative level" should {
      "start LvGridGenerators with the results" in {
        val adminLevel = BoundaryAdminLevel.COUNTY_LEVEL
        val lvCoordinatorRegionCoordinatorAdapter =
          TestProbe[LvRegionResponse]()
        val lvCoordinatorGridGeneratorAdapter =
          TestProbe[LvGridResponse]()

        val testKit = BehaviorTestKit(
          LvRegionCoordinator()
        )

        val lvConfigCapped = lvConfig.copy(
          boundaryAdminLevel =
            lvConfig.boundaryAdminLevel.copy(lowest = adminLevel.osmLevel)
        )

        testKit.run(
          Partition(
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
            .expectEffectType[SpawnedAnonymous[LvGridRequest]]
        }
      }
    }
  }

}
