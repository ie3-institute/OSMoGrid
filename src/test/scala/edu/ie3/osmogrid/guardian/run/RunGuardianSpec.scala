/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */
package edu.ie3.osmogrid.guardian.run

import org.apache.pekko.actor.testkit.typed.CapturedLogEvent
import org.apache.pekko.actor.testkit.typed.Effect.{
  MessageAdapter,
  Spawned,
  WatchedWith
}
import org.apache.pekko.actor.testkit.typed.scaladsl.{
  BehaviorTestKit,
  ScalaTestWithActorTestKit
}
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.osmogrid.cfg.ConfigFailFastSpec.viableConfigurationString
import edu.ie3.osmogrid.cfg.OsmoGridConfigFactory
import edu.ie3.osmogrid.exception.IllegalConfigException
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.osmogrid.io.input
import edu.ie3.osmogrid.io.input.{InputRequest, InputTerminate}
import edu.ie3.osmogrid.io.output.{GridResult, ResultListenerProtocol}
import edu.ie3.osmogrid.lv._
import edu.ie3.osmogrid.mv._
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
          Seq.empty[ActorRef[ResultListenerProtocol]],
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
          .parseWithoutFallback(
            viableConfigurationString.replaceAll("(?m)^.*input.asset.*$", "")
          )
          .getOrElse(fail("Unable to parse malicious config"))
        val maliciousIdleTestKit = BehaviorTestKit(
          RunGuardian(
            maliciousConfig,
            Seq.empty[ActorRef[ResultListenerProtocol]],
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
          .expectEffectType[MessageAdapter[LvResponse, LvRequest]]
        idleTestKit
          .expectEffectType[MessageAdapter[MvResponse, MvRequest]]

        /* Check if I/O actors and LvCoordinator are spawned and watched correctly */
        idleTestKit.expectEffectPF {
          case Spawned(
                _: Behavior[_],
                name,
                _
              ) =>
            name shouldBe s"InputDataProvider_$runId"
        }
        idleTestKit
          .expectEffectType[WatchedWith[InputRequest, RunWatch]]
        idleTestKit.expectEffectPF {
          case Spawned(
                _: Behavior[_],
                name,
                _
              ) =>
            name shouldBe s"PersistenceResultListener_$runId"
        }
        idleTestKit
          .expectEffectType[WatchedWith[ResultListenerProtocol, RunWatch]]

        idleTestKit.expectEffectPF { case Spawned(_: Behavior[_], name, _) =>
          name shouldBe s"LvCoordinator_$runId"
        }
        idleTestKit.expectEffectType[WatchedWith[LvRequest, RunWatch]]

        idleTestKit.expectEffectPF { case Spawned(_: Behavior[_], name, _) =>
          name shouldBe s"MvCoordinator_$runId"
        }
        idleTestKit.expectEffectType[WatchedWith[MvRequest, RunWatch]]

        /* Check for child messages */
        idleTestKit
          .childInbox[LvRequest](s"LvCoordinator_$runId")
          .receiveAll()
          .contains(ReqLvGrids) shouldBe true

        idleTestKit
          .childInbox[MvRequest](s"MvCoordinator_$runId")
          .receiveAll()
          .contains(ReqMvGrids) shouldBe true
      }
    }

    "being in running state" should {
      val running = PrivateMethod[Behavior[RunRequest]](Symbol("running"))

      /* Test probes */
      val lvCoordinatorAdapter = testKit.createTestProbe[LvResponse]()
      val mvCoordinatorAdapter = testKit.createTestProbe[MvResponse]()
      val inputDataProvider =
        testKit.createTestProbe[input.InputDataEvent]()
      val resultListener = testKit.createTestProbe[ResultListenerProtocol]()
      val lvCoordinator = testKit.createTestProbe[LvRequest]()
      val mvCoordinator = testKit.createTestProbe[MvRequest]()

      /* State data */
      val runGuardianData = RunGuardianData(
        runId,
        validConfig,
        Seq.empty[ActorRef[ResultListenerProtocol]],
        MessageAdapters(lvCoordinatorAdapter.ref, mvCoordinatorAdapter.ref)
      )
      val childReferences = ChildReferences(
        inputDataProvider.ref,
        Some(resultListener.ref),
        Seq.empty,
        Some(lvCoordinator.ref),
        Some(mvCoordinator.ref)
      )
      val finishedGridData = FinishedGridData.empty

      val runningTestKit = BehaviorTestKit(
        RunGuardian invokePrivate running(
          runGuardianData,
          childReferences,
          finishedGridData
        )
      )

      "log an error if faced to not supported request" in {
        runningTestKit.run(Run)

        runningTestKit.logEntries() should contain only CapturedLogEvent(
          Level.ERROR,
          s"Received a message, that I don't understand during active run $runId.\n\tMessage: $Run"
        )
      }

      "handles an incoming lv result" in new GridSupport {
        val lvGrids: Seq[SubGridContainer] = Seq(mockSubGrid(1))
        val streetGraph: OsmGraph = new OsmGraph()

        runningTestKit.run(
          MessageAdapters.WrappedLvCoordinatorResponse(
            RepLvGrids(lvGrids, streetGraph)
          )
        )

        /* Event is logged */
        runningTestKit.logEntries() should contain(
          CapturedLogEvent(
            Level.INFO,
            s"Received lv grids."
          )
        )

        mvCoordinator.expectMessage(
          WrappedMvResponse(ProvidedLvData(lvGrids, streetGraph))
        )
      }

      "handles an incoming mv result" in new GridSupport {
        runningTestKit.run(
          MessageAdapters.WrappedMvCoordinatorResponse(
            RepMvGrids(Seq(mockSubGrid(100)), None, Map.empty, assetInformation)
          )
        )

        /* Event is logged */
        runningTestKit.logEntries() should contain(
          CapturedLogEvent(
            Level.INFO,
            s"Received mv grids."
          )
        )
      }

      "handle all received grid results" in {
        runningTestKit.run(HandleGridResults)

        /* Result is forwarded to listener */
        resultListener.expectMessageType[GridResult]
      }

      "being in stopping state without a LvCoordinator" should {
        val stopping = PrivateMethod[Behavior[RunRequest]](Symbol("stopping"))

        val stoppingData = StoppingData(
          runId,
          inputDataProviderTerminated = false,
          resultListenerTerminated = false,
          lvCoordinatorTerminated = None,
          mvCoordinatorTerminated = None
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
        val stopping = PrivateMethod[Behavior[RunRequest]](Symbol("stopping"))

        val stoppingData = StoppingData(
          runId,
          inputDataProviderTerminated = false,
          resultListenerTerminated = false,
          lvCoordinatorTerminated = Some(false),
          mvCoordinatorTerminated = Some(false)
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

          stoppingTestKit.isAlive shouldBe true

          stoppingTestKit.run(MvCoordinatorDied)

          stoppingTestKit.isAlive shouldBe false
        }
      }
    }

    "initiate coordinated shutdown, if somebody unexpectedly dies" in {
      val running = PrivateMethod[Behavior[RunRequest]](Symbol("running"))

      /* Test probes */
      val lvCoordinatorAdapter = testKit.createTestProbe[LvResponse]()
      val mvCoordinatorAdapter = testKit.createTestProbe[MvResponse]()
      val inputDataProvider =
        testKit.createTestProbe[input.InputDataEvent]()
      val resultListener = testKit.createTestProbe[ResultListenerProtocol]()
      val lvCoordinator = testKit.createTestProbe[LvRequest]()
      val mvCoordinator = testKit.createTestProbe[MvRequest]()

      /* State data */
      val runGuardianData = RunGuardianData(
        runId,
        validConfig,
        Seq.empty[ActorRef[ResultListenerProtocol]],
        MessageAdapters(lvCoordinatorAdapter.ref, mvCoordinatorAdapter.ref)
      )
      val childReferences = ChildReferences(
        inputDataProvider.ref,
        Some(resultListener.ref),
        Seq.empty,
        Some(lvCoordinator.ref),
        Some(mvCoordinator.ref)
      )
      val finishedGridData = FinishedGridData.empty

      val runningTestKit = BehaviorTestKit(
        RunGuardian invokePrivate running(
          runGuardianData,
          childReferences,
          finishedGridData
        )
      )

      runningTestKit.run(LvCoordinatorDied)

      /* Event is logged */
      runningTestKit.logEntries() should contain(
        CapturedLogEvent(
          Level.WARN,
          s"Lv coordinator for run $runId unexpectedly died. Start coordinated shut down phase for this run."
        )
      )
      /* All children are sent a termination request */
      lvCoordinator.expectMessage(LvTerminate)
      mvCoordinator.expectMessage(MvTerminate)
      inputDataProvider.expectMessage(InputTerminate)
    }
  }
}
