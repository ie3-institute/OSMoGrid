/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.mv

import akka.actor.testkit.typed.CapturedLogEvent
import akka.actor.testkit.typed.scaladsl.{
  ActorTestKit,
  BehaviorTestKit,
  ScalaTestWithActorTestKit
}
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.osmogrid.cfg.OsmoGridConfigFactory
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.test.common.{GridSupport, MvTestData, UnitSpec}
import org.scalatest.BeforeAndAfterAll
import org.slf4j.event.Level

class MvCoordinatorSpec
    extends ScalaTestWithActorTestKit
    with UnitSpec
    with BeforeAndAfterAll
    with MvTestData
    with GridSupport {
  private val asynchronousTestKit = ActorTestKit()
  private val runGuardian =
    asynchronousTestKit.createTestProbe[MvResponse]("RunGuardian")
  private val cfg =
    OsmoGridConfigFactory.defaultTestConfig.generation.mv.getOrElse(
      fail("Test config does not contain config for mv grid generation.")
    )

  "MvCoordinator" should {
    "be applied correctly" in {
      val coordinator = BehaviorTestKit(
        MvCoordinator(
          cfg,
          runGuardian.ref
        )
      )

      coordinator.isAlive shouldBe true
    }

    "terminate if asked to do so" in {
      val idleTestKit = BehaviorTestKit(
        MvCoordinator(
          cfg,
          runGuardian.ref
        )
      )

      idleTestKit.run(MvTerminate)

      idleTestKit.logEntries() should contain only CapturedLogEvent(
        Level.INFO,
        "Got request to terminate."
      )
    }
  }

  "A mv coordinator working correctly" should {
    val idleTestKit: BehaviorTestKit[MvRequest] =
      BehaviorTestKit(MvCoordinator(cfg, runGuardian.ref))

    val lvGrids: Seq[SubGridContainer] = Seq(simpleSubGrid(1))
    val streetGraph = new OsmGraph()

    "receive grid request" in {
      idleTestKit.run(ReqMvGrids)

      idleTestKit.logEntries() should contain allOf (
        CapturedLogEvent(
          Level.INFO,
          s"Starting generation of medium voltage grids!"
        ),
        CapturedLogEvent(Level.DEBUG, s"Waiting for input data.")
      )
    }

    "receive lv data" in {
      idleTestKit.run(WrappedMvResponse(ProvidedLvData(lvGrids, streetGraph)))

      idleTestKit.logEntries() should contain allOf (
        CapturedLogEvent(Level.DEBUG, s"Received lv data."),
        CapturedLogEvent(
          Level.DEBUG,
          s"All awaited mv data is present. Start processing."
        )
      )
    }

    "start graph generation" in {
      idleTestKit.run(StartGeneration(lvGrids, None, streetGraph))

      idleTestKit.logEntries() should contain allOf (
        CapturedLogEvent(
          Level.DEBUG,
          s"Starting medium voltage graph generation."
        ),
        CapturedLogEvent(
          Level.DEBUG,
          s"Given area was split into 1 polygon(s)."
        )
      )
    }

    "send all finished results to the guardian" in {
      val mvGrid: SubGridContainer = mockSubGrid(100)

      idleTestKit.run(
        WrappedMvResponse(FinishedMvGridData(mvGrid, Seq.empty, Seq.empty))
      )

      idleTestKit.logEntries() should contain(
        CapturedLogEvent(
          Level.INFO,
          s"Received all expected grids! Will report back SubGridContainers"
        )
      )

    }
  }
}
