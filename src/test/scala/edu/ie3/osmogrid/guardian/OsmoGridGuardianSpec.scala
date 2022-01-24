/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.guardian

import akka.actor.testkit.typed.CapturedLogEvent
import akka.actor.testkit.typed.Effect.{MessageAdapter, Spawned}
import akka.actor.testkit.typed.scaladsl.BehaviorTestKit
import akka.actor.typed.{ActorRef, Behavior}
import edu.ie3.osmogrid.cfg.OsmoGridConfigFactory
import edu.ie3.osmogrid.guardian.OsmoGridGuardian.RunGuardianDied
import edu.ie3.osmogrid.guardian.run.RunGuardian
import edu.ie3.osmogrid.guardian.run.Request
import edu.ie3.osmogrid.io.output.ResultListener.ResultEvent
import edu.ie3.test.common.UnitSpec
import org.slf4j.event.Level

import java.util.UUID

class OsmoGridGuardianSpec extends UnitSpec {
  "Having an overall OsmoGridGuardian" when {
    "being idle" should {
      val guardianData = OsmoGridGuardian.GuardianData(Seq.empty[UUID])
      val config = OsmoGridConfigFactory.defaultTestConfig
      val additionalListeners = Seq.empty[ActorRef[ResultEvent]]
      val runId = UUID.randomUUID()

      val idleTestKit = BehaviorTestKit(OsmoGridGuardian.idle(guardianData))

      "spawn a new RunGuardian on request" in {
        idleTestKit.run(
          OsmoGridGuardian.Run(config, additionalListeners, runId)
        )

        /* Check if the right child is spawned */
        idleTestKit.expectEffectPF {
          case Spawned(childBehav: Behavior[Request], name, props) =>
            name shouldBe s"RunGuardian_$runId"
          case Spawned(childBehav, _, _) =>
            fail(s"Spawned a child with wrong behavior '$childBehav'.")
        }
      }

      "report a dead RunGuardian" in {
        idleTestKit.run(RunGuardianDied(runId))

        idleTestKit.logEntries() should contain only CapturedLogEvent(
          Level.INFO,
          s"Run $runId terminated."
        )
      }
    }
  }
}
