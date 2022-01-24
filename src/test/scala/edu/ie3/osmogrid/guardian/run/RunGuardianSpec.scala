/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.guardian.run

import akka.actor.testkit.typed.CapturedLogEvent
import akka.actor.testkit.typed.Effect.{MessageAdapter, Spawned, WatchedWith}
import akka.actor.testkit.typed.scaladsl.BehaviorTestKit
import akka.actor.typed.{ActorRef, Behavior}
import edu.ie3.osmogrid.cfg.OsmoGridConfigFactory
import edu.ie3.osmogrid.exception.IllegalConfigException
import edu.ie3.osmogrid.io.input.InputDataProvider
import edu.ie3.osmogrid.io.output.ResultListener.ResultEvent
import edu.ie3.osmogrid.lv.LvCoordinator
import edu.ie3.osmogrid.lv.LvCoordinator.ReqLvGrids
import edu.ie3.test.common.UnitSpec
import org.slf4j.event.Level

import java.util.UUID

class RunGuardianSpec extends UnitSpec {
  "Having a run guardian" when {
    val runId = UUID.randomUUID()
    val validConfig = OsmoGridConfigFactory.defaultTestConfig

    "being idle" should {
      val idleTestKit = BehaviorTestKit(
        RunGuardian(validConfig, Seq.empty[ActorRef[ResultEvent]], runId)
      )

      "log an error if faced to not supported request" in {
        idleTestKit.run(ResultEventListenerDied)

        idleTestKit.logEntries() should contain only CapturedLogEvent(
          Level.ERROR,
          s"Received a message, that I don't understand during idle phase of run $runId.\n\tMessage: $ResultEventListenerDied"
        )
      }

      "log an error, if initiation of a run is impossible" in {
        val maliciousConfig = OsmoGridConfigFactory
          .parseWithoutFallback("")
          .getOrElse(fail("Unable to parse malicious config"))
        val maliciousIdleTestKit = BehaviorTestKit(
          RunGuardian(maliciousConfig, Seq.empty[ActorRef[ResultEvent]], runId)
        )

        maliciousIdleTestKit.run(Run)
        maliciousIdleTestKit.logEntries() should contain only CapturedLogEvent(
          Level.ERROR,
          s"Unable to start run $runId.",
          Some(
            IllegalConfigException(
              "You have to provide at least one input data type for asset information!"
            )
          ),
          None
        )
      }

      "spawns children, if input is fine" in {
        idleTestKit.run(Run)

        /* Two message adapters are registered */
        idleTestKit
          .expectEffectType[MessageAdapter[LvCoordinator.Response, Request]]
        idleTestKit.expectEffectType[MessageAdapter[ResultEvent, Request]]

        /* Check if I/O actors and LvCoordinator are spawned and watched correctly */
        idleTestKit.expectEffectPF {
          case Spawned(_: Behavior[InputDataProvider.Request], name, props) =>
            name shouldBe s"InputDataProvider_$runId"
        }
        idleTestKit
          .expectEffectType[WatchedWith[InputDataProvider.Request, Watch]]
        idleTestKit.expectEffectPF {
          case Spawned(_: Behavior[ResultEvent], name, props) =>
            name shouldBe s"PersistenceResultListener_$runId"
        }
        idleTestKit.expectEffectType[WatchedWith[ResultEvent, Watch]]
        idleTestKit.expectEffectPF {
          case Spawned(_: Behavior[LvCoordinator.Request], name, props) =>
            name shouldBe s"LvCoordinator_$runId"
        }
        idleTestKit.expectEffectType[WatchedWith[LvCoordinator.Request, Watch]]

        /* Check for child messages */
        idleTestKit
          .childInbox[LvCoordinator.Request](s"LvCoordinator_$runId")
          .receiveAll()
          .exists { case ReqLvGrids(cfg, replyTo) =>
            validConfig.generation.lv.contains(cfg)
          } shouldBe true
      }
    }
  }
}
