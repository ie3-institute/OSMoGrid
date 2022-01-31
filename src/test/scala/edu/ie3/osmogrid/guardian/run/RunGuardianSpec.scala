/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.guardian.run

import akka.actor.testkit.typed.CapturedLogEvent
import akka.actor.testkit.typed.Effect.{MessageAdapter, Spawned, WatchedWith}
import akka.actor.testkit.typed.scaladsl.{
  BehaviorTestKit,
  ScalaTestWithActorTestKit
}
import akka.actor.typed.{ActorRef, Behavior}
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.osmogrid.cfg.OsmoGridConfigFactory
import edu.ie3.osmogrid.exception.IllegalConfigException
import edu.ie3.osmogrid.io.input.InputDataProvider
import edu.ie3.osmogrid.io.output.{ResultListener, ResultListenerProtocol}
import edu.ie3.osmogrid.lv.LvCoordinator
import edu.ie3.osmogrid.lv.LvCoordinator.ReqLvGrids
import edu.ie3.test.common.{GridSupport, UnitSpec}
import org.slf4j.event.Level

import java.util.UUID

class RunGuardianSpec extends ScalaTestWithActorTestKit with UnitSpec {
  "Having a run guardian" when {
    val runId = UUID.randomUUID()
    val validConfig = OsmoGridConfigFactory.defaultTestConfig

    "being idle state" should {
      val idleTestKit = BehaviorTestKit(
        RunGuardian(
          validConfig,
          Seq.empty[ActorRef[ResultListenerProtocol.Request]],
          runId
        )
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
          RunGuardian(
            maliciousConfig,
            Seq.empty[ActorRef[ResultListenerProtocol.Request]],
            runId
          )
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
        idleTestKit.expectEffectType[
          MessageAdapter[ResultListenerProtocol.Request, Request]
        ]

        /* Check if I/O actors and LvCoordinator are spawned and watched correctly */
        idleTestKit.expectEffectPF {
          case Spawned(_: Behavior[InputDataProvider.Request], name, props) =>
            name shouldBe s"InputDataProvider_$runId"
        }
        idleTestKit
          .expectEffectType[WatchedWith[InputDataProvider.Request, Watch]]
        idleTestKit.expectEffectPF {
          case Spawned(
                _: Behavior[ResultListenerProtocol.Request],
                name,
                props
              ) =>
            name shouldBe s"PersistenceResultListener_$runId"
        }
        idleTestKit
          .expectEffectType[WatchedWith[ResultListenerProtocol.Request, Watch]]
        idleTestKit.expectEffectPF {
          case Spawned(_: Behavior[LvCoordinator.Request], name, props) =>
            name shouldBe s"LvCoordinator_$runId"
        }
        idleTestKit.expectEffectType[WatchedWith[LvCoordinator.Request, Watch]]

        /* Check for child messages */
        idleTestKit
          .childInbox[LvCoordinator.Request](s"LvCoordinator_$runId")
          .receiveAll()
          .exists {
            case ReqLvGrids(cfg, replyTo) =>
              validConfig.generation.lv.contains(cfg)
            case _ => false
          } shouldBe true
      }
    }

    "being in running state" should {
      val running = PrivateMethod[Behavior[Request]](Symbol("running"))

      /* Test probes */
      val lvCoordinatorAdapter =
        testKit.createTestProbe[LvCoordinator.Response]()
      val inputDataProvider =
        testKit.createTestProbe[InputDataProvider.Request]()
      val lvCoordinator = testKit.createTestProbe[LvCoordinator.Request]()

      /* State data */
      val runGuardianData = RunGuardianData(
        runId,
        validConfig,
        Seq.empty[ActorRef[ResultListenerProtocol.Request]],
        MessageAdapters(lvCoordinatorAdapter.ref)
      )
      val childReferences = ChildReferences(
        inputDataProvider.ref,
        Some(lvCoordinator.ref)
      )

      val runningTestKit = BehaviorTestKit(
        RunGuardian invokePrivate running(runGuardianData, childReferences)
      )

      "log an error if faced to not supported request" in {
        runningTestKit.run(Run)

        runningTestKit.logEntries() should contain only CapturedLogEvent(
          Level.ERROR,
          s"Received a message, that I don't understand during active run $runId.\n\tMessage: $Run"
        )
      }
    }

    "being in stopping state without a LvCoordinator" should {
      val stopping = PrivateMethod[Behavior[Request]](Symbol("stopping"))

      val stoppingData = StoppingData(
        runId,
        inputDataProviderTerminated = false,
        resultListenerTerminated = false,
        lvCoordinatorTerminated = None
      )

      val stoppingTestKit = BehaviorTestKit(
        RunGuardian invokePrivate stopping(stoppingData)
      )

      "log an error if faced to not supported request" in {
        stoppingTestKit.run(Run)

        stoppingTestKit.logEntries() should contain only CapturedLogEvent(
          Level.ERROR,
          s"Received a message, that I don't understand during coordinated shutdown phase of run $runId.\n\tMessage: $Run"
        )
      }

      "stop itself only once all awaited termination messages have been received" in {
        stoppingTestKit.run(ResultEventListenerDied)

        stoppingTestKit.isAlive shouldBe true

        stoppingTestKit.run(InputDataProviderDied)

        stoppingTestKit.isAlive shouldBe false
      }
    }

    "being in stopping state with a LvCoordinator" should {
      val stopping = PrivateMethod[Behavior[Request]](Symbol("stopping"))

      val stoppingData = StoppingData(
        runId,
        inputDataProviderTerminated = false,
        resultListenerTerminated = false,
        lvCoordinatorTerminated = Some(false)
      )

      val stoppingTestKit = BehaviorTestKit(
        RunGuardian invokePrivate stopping(stoppingData)
      )

      "stop itself only once all awaited termination messages have been received" in {
        stoppingTestKit.run(InputDataProviderDied)

        stoppingTestKit.isAlive shouldBe true

        stoppingTestKit.run(ResultEventListenerDied)

        stoppingTestKit.isAlive shouldBe true

        stoppingTestKit.run(LvCoordinatorDied)

        stoppingTestKit.isAlive shouldBe false
      }
    }
  }
}
