/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import akka.actor.testkit.typed.scaladsl.{
  ActorTestKit,
  BehaviorTestKit,
  ScalaTestWithActorTestKit
}
import edu.ie3.osmogrid.lv.LvGridGenerator.RepLvGrid
import edu.ie3.osmogrid.lv.region_coordinator.LvTestModel
import edu.ie3.osmogrid.model.OsmTestData
import edu.ie3.test.common.UnitSpec

import java.util.UUID

class GridGeneratorSpec
    extends ScalaTestWithActorTestKit
    with UnitSpec
    with OsmTestData {
  private val asynchronousTestKit = ActorTestKit()

  "A grid generator" should {

    "build an lv grid upon request" in {
      val lvCoordinator = asynchronousTestKit
        .createTestProbe[LvGridGenerator.Response]("GridGeneratorAdapter")

      val idleTestKit = BehaviorTestKit(
        LvGridGenerator()
      )

      val gridUuid = UUID.randomUUID()
      val osmData = TestLvOsmoGridModel.lvOsmoGridModel
      val assetInformation = LvTestModel.assetInformation
      val config = LvTestModel.lvConfig
      val generateGrid = LvGridGenerator.GenerateGrid(
        lvCoordinator.ref,
        gridUuid,
        osmData,
        assetInformation,
        config
      )

      idleTestKit.run(generateGrid)

      lvCoordinator.expectMessageType[RepLvGrid] match {
        case RepLvGrid(gridUuid, grid) =>
          gridUuid shouldBe gridUuid
          grid.getRawGrid.getNodes.size() > 0 shouldBe true
          grid.getSystemParticipants.getLoads.size() > 0 shouldBe true
      }
    }
  }
}
