/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import akka.actor.testkit.typed.CapturedLogEvent
import akka.actor.testkit.typed.Effect.{MessageAdapter, SpawnedAnonymous}
import akka.actor.testkit.typed.scaladsl.{
  ActorTestKit,
  BehaviorTestKit,
  ScalaTestWithActorTestKit
}
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import edu.ie3.datamodel.models.input.connector.`type`.{
  LineTypeInput,
  Transformer2WTypeInput
}
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.osmogrid.cfg.OsmoGridConfigFactory
import edu.ie3.osmogrid.exception.RequestFailedException
import edu.ie3.osmogrid.io.input
import edu.ie3.osmogrid.io.input.{
  AssetInformation,
  BoundaryAdminLevel,
  InputResponse
}
import edu.ie3.osmogrid.lv
import edu.ie3.osmogrid.lv.LvMessageAdapters.{
  WrappedGridGeneratorResponse,
  WrappedInputDataResponse,
  WrappedRegionResponse
}
import edu.ie3.osmogrid.lv.region_coordinator.LvTestModel.assetInformation
import edu.ie3.osmogrid.lv.region_coordinator.{
  GridToExpect,
  LvRegionRequest,
  LvRegionResponse,
  Partition
}
import edu.ie3.osmogrid.model.OsmoGridModel.LvOsmoGridModel
import edu.ie3.osmogrid.model.SourceFilter.LvFilter
import edu.ie3.test.common.UnitSpec
import org.scalatest.BeforeAndAfterAll
import org.scalatestplus.mockito.MockitoSugar.mock
import org.slf4j.event.Level

import java.util.UUID
import scala.collection.parallel.ParSeq

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
      asynchronousTestKit.createTestProbe[input.InputDataEvent](
        "InputDataProvider"
      )
    val lvCoordinatorAdapter =
      asynchronousTestKit.createTestProbe[LvResponse](
        "RunGuardian"
      )
    val inputDataProviderAdapter =
      asynchronousTestKit.createTestProbe[InputResponse](
        "InputDataProviderAdapter"
      )
    val regionCoordinatorAdapter =
      asynchronousTestKit.createTestProbe[LvRegionResponse](
        "InputDataProviderAdapter"
      )
    val gridGeneratorAdapter =
      asynchronousTestKit.createTestProbe[LvGridResponse](
        "GridGeneratorAdapter"
      )
    val msgAdapters = LvMessageAdapters(
      inputDataProviderAdapter.ref,
      regionCoordinatorAdapter.ref,
      gridGeneratorAdapter.ref
    )

    "being initialized" should {

      "register the correct amount of message adapters" in {
        val idleTestKit = BehaviorTestKit(
          LvCoordinator(
            cfg,
            inputDataProvider.ref,
            lvCoordinatorAdapter.ref
          )
        )

        idleTestKit.expectEffectType[
          MessageAdapter[InputResponse, LvRequest]
        ]
        idleTestKit.expectEffectType[
          MessageAdapter[LvRegionResponse, LvRequest]
        ]
      }
    }

    "being idle" should {
      "terminate if asked to do so" in {
        val idleTestKit = BehaviorTestKit(
          lv.LvCoordinator(
            cfg,
            inputDataProvider.ref,
            lvCoordinatorAdapter.ref
          )
        )
        idleTestKit.run(LvTerminate)

        idleTestKit.logEntries() should contain only CapturedLogEvent(
          Level.INFO,
          "Got request to terminate."
        )
        idleTestKit.isAlive shouldBe false
      }

      "ask for osm and asset information upon request" in {
        val idleTestKit = BehaviorTestKit(
          lv.LvCoordinator(
            cfg,
            inputDataProvider.ref,
            lvCoordinatorAdapter.ref
          )
        )
        idleTestKit.run(ReqLvGrids)

        /* Receive exactly two messages, that are requests for OSM and asset data */
        inputDataProvider.receiveMessages(2).forall {
          case _: input.ReqOsm =>
            true
          case _: input.ReqAssetTypes =>
            true
          case _ => false
        } shouldBe true
      }
    }

    "awaiting input data" should {
      "terminate if asked to do so" in {
        val awaitingTestKit = BehaviorTestKit(
          LvCoordinator invokePrivate PrivateMethod[Behavior[LvRequest]](
            Symbol(
              "awaitInputData"
            )
          )(
            AwaitingData.empty(
              IdleData(
                cfg,
                inputDataProvider.ref,
                lvCoordinatorAdapter.ref,
                msgAdapters
              )
            )
          )
        )

        awaitingTestKit.run(LvTerminate)

        awaitingTestKit.logEntries() should contain only CapturedLogEvent(
          Level.INFO,
          "Got request to terminate."
        )
        awaitingTestKit.isAlive shouldBe false
      }

      "terminate, if a request has been answered with a failure" in {
        val awaitingTestKit = BehaviorTestKit(
          LvCoordinator invokePrivate PrivateMethod[Behavior[LvRequest]](
            Symbol(
              "awaitInputData"
            )
          )(
            AwaitingData.empty(
              IdleData(
                cfg,
                inputDataProvider.ref,
                lvCoordinatorAdapter.ref,
                msgAdapters
              )
            )
          )
        )

        /* Send a failed request response */
        val exc = new RuntimeException("Some random failure.")
        awaitingTestKit.run(
          WrappedInputDataResponse(
            input.OsmReadFailed(
              exc
            )
          )
        )

        awaitingTestKit.logEntries() should contain only CapturedLogEvent(
          Level.ERROR,
          "Request of needed input data failed. Stop low voltage grid generation.",
          Some(
            RequestFailedException(
              "The requested OSM data cannot be read. Stop generation. Exception:",
              exc
            )
          ),
          None
        )
        awaitingTestKit.isAlive shouldBe false
      }

      "terminate, if an asset request has been answered with a failure" in {
        val awaitingTestKit = BehaviorTestKit(
          LvCoordinator invokePrivate PrivateMethod[Behavior[LvRequest]](
            Symbol(
              "awaitInputData"
            )
          )(
            AwaitingData.empty(
              IdleData(
                cfg,
                inputDataProvider.ref,
                lvCoordinatorAdapter.ref,
                msgAdapters
              )
            )
          )
        )

        /* Send a failed request response */
        val exc = new RuntimeException("Some random failure.")
        awaitingTestKit.run(
          WrappedInputDataResponse(
            input.AssetReadFailed(
              exc
            )
          )
        )

        awaitingTestKit.logEntries() should contain only CapturedLogEvent(
          Level.ERROR,
          "Request of needed input data failed. Stop low voltage grid generation.",
          Some(
            RequestFailedException(
              "The requested asset data cannot be read. Stop generation. Exception:",
              exc
            )
          ),
          None
        )
        awaitingTestKit.isAlive shouldBe false
      }

      "spawn a child actor only if all data has arrived" in {

        val awaitingData = AwaitingData.empty(
          IdleData(
            cfg,
            inputDataProvider.ref,
            lvCoordinatorAdapter.ref,
            msgAdapters
          )
        )

        val runId = UUID.randomUUID()
        val awaitingTestKit = BehaviorTestKit[LvRequest](
          LvCoordinator invokePrivate PrivateMethod[Behavior[LvRequest]](
            Symbol(
              "awaitInputData"
            )
          )(awaitingData)
        )
        val osmData = input.RepOsm(
          LvOsmoGridModel(
            ParSeq.empty,
            ParSeq.empty,
            ParSeq.empty,
            ParSeq.empty,
            ParSeq.empty,
            LvFilter()
          )
        )

        awaitingTestKit.run(WrappedInputDataResponse(osmData))
        awaitingTestKit.hasEffects() shouldBe false

        awaitingTestKit.run(
          WrappedInputDataResponse(
            input.RepAssetTypes(
              AssetInformation(
                Seq.empty[LineTypeInput],
                Seq.empty[Transformer2WTypeInput]
              )
            )
          )
        )
        /* Test for spawned child */
        awaitingTestKit.expectEffectPF {
          case _: SpawnedAnonymous[_] =>
            succeed
          case unexpected => fail(s"Unexpected Effect happened: $unexpected")
        }

        awaitingTestKit.selfInbox().receiveMessage() match {
          case StartLvGeneration(lvConfig, _, _, _) => lvConfig shouldBe cfg
          case unexpected => fail(s"Received unexpected message '$unexpected'.")
        }
      }
    }

    "awaiting results" should {
      "terminate if asked to do so" in {
        val awaitingTestKit = BehaviorTestKit(
          LvCoordinator invokePrivate PrivateMethod[Behavior[LvRequest]](
            Symbol("awaitResults")
          )(
            lvCoordinatorAdapter.ref,
            msgAdapters,
            ResultData.empty
          )
        )

        awaitingTestKit.run(LvTerminate)

        awaitingTestKit.logEntries() should contain only CapturedLogEvent(
          Level.INFO,
          "Got request to terminate."
        )
        awaitingTestKit.isAlive shouldBe false
      }

      "properly perform generation" in {
        val awaitingTestKit = BehaviorTestKit[LvRequest](
          LvCoordinator invokePrivate PrivateMethod[Behavior[LvRequest]](
            Symbol("awaitResults")
          )(
            lvCoordinatorAdapter.ref,
            msgAdapters,
            ResultData.empty
          )
        )
        /* Mocking the lv region generator */
        val gridUuid = UUID.randomUUID()
        val mockedSubgrid = mock[SubGridContainer]
        val gridToExpect =
          GridToExpect(gridUuid)
        val mockedBehavior: Behavior[LvRegionRequest] =
          Behaviors.receive[LvRegionRequest] { case (ctx, msg) =>
            msg match {
              case Partition(_, _, _, _, replyTo, _) =>
                ctx.log.info(
                  s"Received the following message: '$msg'. Send out reply."
                )
                replyTo ! gridToExpect
            }
            Behaviors.same
          }

        val regionCoordinatorProbe =
          asynchronousTestKit.createTestProbe[LvRegionRequest]()
        val mockedLvRegionCoordinator = asynchronousTestKit.spawn(
          Behaviors.monitor(regionCoordinatorProbe.ref, mockedBehavior)
        )

        val lvOsmoGridModel = LvOsmoGridModel(
          ParSeq.empty,
          ParSeq.empty,
          ParSeq.empty,
          ParSeq.empty,
          ParSeq.empty,
          LvFilter()
        )

        /* Ask the coordinator to start the process */
        awaitingTestKit.run(
          StartLvGeneration(
            cfg,
            mockedLvRegionCoordinator,
            lvOsmoGridModel,
            assetInformation
          )
        )
        regionCoordinatorProbe
          .expectMessageType[Partition] match {
          case Partition(
                osmoGridModel,
                _,
                administrativeLevel,
                config,
                _,
                _
              ) =>
            osmoGridModel shouldBe lvOsmoGridModel
            administrativeLevel shouldBe BoundaryAdminLevel.NATION_LEVEL
            config shouldBe cfg
        }

        /* The mocked behavior directly sends a reply -> Check that out */
        val regionResponse = regionCoordinatorAdapter
          .expectMessageType[GridToExpect]
        regionResponse shouldBe gridToExpect
        awaitingTestKit.run(
          WrappedRegionResponse(regionResponse)
        ) // Forward from probe to actor

        /* The LvCoordinator now waits for the expected grid to be generated */
        val repLvGrid = RepLvGrid(gridUuid, Seq(mockedSubgrid))
        awaitingTestKit.run(
          WrappedGridGeneratorResponse(repLvGrid)
        )

        /* The result is forwarded once we received all the grids we expect*/
        lvCoordinatorAdapter.expectMessageType[RepLvGrids] match {
          case RepLvGrids(grids) =>
            grids should contain theSameElementsAs Seq(mockedSubgrid)
        }

        awaitingTestKit.isAlive shouldBe false
      }
    }
  }

  override protected def afterAll(): Unit = {
    asynchronousTestKit.shutdownTestKit()
    super.afterAll()
  }
}
