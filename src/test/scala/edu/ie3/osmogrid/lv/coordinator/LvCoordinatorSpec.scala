/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv.coordinator

import akka.actor.testkit.typed.CapturedLogEvent
import akka.actor.testkit.typed.Effect.MessageAdapter
import akka.actor.testkit.typed.scaladsl.{
  ActorTestKit,
  BehaviorTestKit,
  ScalaTestWithActorTestKit
}
import akka.actor.typed.{ActorRef, Behavior}
import edu.ie3.osmogrid.cfg.OsmoGridConfigFactory
import edu.ie3.osmogrid.guardian.run.RunGuardian
import edu.ie3.osmogrid.io.input.InputDataProvider
import edu.ie3.osmogrid.io.output.ResultListener.ResultEvent
import edu.ie3.osmogrid.lv.{LvRegionCoordinator, coordinator}
import edu.ie3.test.common.UnitSpec
import org.scalatest.BeforeAndAfterAll
import org.slf4j.event.Level

class LvCoordinatorSpec
    extends ScalaTestWithActorTestKit
    with UnitSpec
    with BeforeAndAfterAll {
  private val asynchronousTestKit = ActorTestKit()

  "Having a lv coordinator" when {
    val cfg = OsmoGridConfigFactory.defaultTestConfig.generation.lv.getOrElse(
      fail("Test config does not contain config for lv grid generation.")
    )
    val inputDataProvider =
      asynchronousTestKit.createTestProbe[InputDataProvider.Request](
        "InputDataProvider"
      )
    val lvCoordinatorAdapter =
      asynchronousTestKit.createTestProbe[coordinator.Response](
        "RunGuardian"
      )

    "being initialized" should {

      "register the correct amount of message adapters" in {
        val idleTestKit = BehaviorTestKit(
          coordinator.LvCoordinator(
            cfg,
            inputDataProvider.ref,
            lvCoordinatorAdapter.ref
          )
        )

        idleTestKit.expectEffectType[
          MessageAdapter[InputDataProvider.Response, coordinator.Request]
        ]
        idleTestKit.expectEffectType[
          MessageAdapter[LvRegionCoordinator.Response, coordinator.Request]
        ]
      }
    }

    "being idle" should {
      "terminate if asked to do so" in {
        val idleTestKit = BehaviorTestKit(
          coordinator.LvCoordinator(
            cfg,
            inputDataProvider.ref,
            lvCoordinatorAdapter.ref
          )
        )
        idleTestKit.run(coordinator.Terminate)

        idleTestKit.logEntries() should contain only CapturedLogEvent(
          Level.INFO,
          "Got request to terminate."
        )
        idleTestKit.isAlive shouldBe false
      }

      val idleTestKit = BehaviorTestKit(
        coordinator.LvCoordinator(
          cfg,
          inputDataProvider.ref,
          lvCoordinatorAdapter.ref
        )
      )
      "ask for osm and asset information upon request" in {
        idleTestKit.run(coordinator.ReqLvGrids)

        /* Receive exactly two messages, that are requests for OSM and asset data */
        inputDataProvider.receiveMessages(2).forall {
          case _: InputDataProvider.ReqOsm =>
            true
          case _: InputDataProvider.ReqAssetTypes =>
            true
          case _ => false
        } shouldBe true
      }
    }
  }

  override protected def afterAll(): Unit = {
    asynchronousTestKit.shutdownTestKit()
    super.afterAll()
  }
}
