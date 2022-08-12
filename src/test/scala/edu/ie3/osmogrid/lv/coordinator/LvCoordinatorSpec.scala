/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv.coordinator

import akka.actor.testkit.typed.CapturedLogEvent
import akka.actor.testkit.typed.Effect.{MessageAdapter, SpawnedAnonymous}
import akka.actor.testkit.typed.scaladsl.{
  ActorTestKit,
  BehaviorTestKit,
  ScalaTestWithActorTestKit
}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.Behavior
import edu.ie3.datamodel.models.input.connector.`type`.{
  LineTypeInput,
  Transformer2WTypeInput
}
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.osmogrid.cfg.OsmoGridConfigFactory
import edu.ie3.osmogrid.exception.RequestFailedException
import edu.ie3.osmogrid.io.input.InputDataProvider.AssetInformation
import edu.ie3.osmogrid.io.input.{BoundaryAdminLevel, InputDataProvider}
import edu.ie3.osmogrid.lv.LvGridGenerator.{GenerateGrid, RepLvGrid}
import edu.ie3.osmogrid.lv.region_coordinator.LvRegionCoordinator.{
  GridToExpect,
  Partition
}
import edu.ie3.osmogrid.lv.coordinator.MessageAdapters.{
  WrappedGridGeneratorResponse,
  WrappedInputDataResponse,
  WrappedRegionResponse
}
import edu.ie3.osmogrid.lv.{LvGridGenerator, coordinator}
import edu.ie3.osmogrid.lv.coordinator.LvCoordinator.ResultData
import edu.ie3.osmogrid.lv.region_coordinator.LvRegionCoordinator
import edu.ie3.osmogrid.lv.region_coordinator.LvTestModel.assetInformation
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
      asynchronousTestKit.createTestProbe[InputDataProvider.InputDataEvent](
        "InputDataProvider"
      )
    val lvCoordinatorAdapter =
      asynchronousTestKit.createTestProbe[coordinator.Response](
        "RunGuardian"
      )
    val inputDataProviderAdapter =
      asynchronousTestKit.createTestProbe[InputDataProvider.Response](
        "InputDataProviderAdapter"
      )
    val regionCoordinatorAdapter =
      asynchronousTestKit.createTestProbe[LvRegionCoordinator.Response](
        "InputDataProviderAdapter"
      )
    val gridGeneratorAdapter =
      asynchronousTestKit.createTestProbe[LvGridGenerator.Response](
        "GridGeneratorAdapter"
      )
    val msgAdapters = MessageAdapters(
      inputDataProviderAdapter.ref,
      regionCoordinatorAdapter.ref,
      gridGeneratorAdapter.ref
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

      "ask for osm and asset information upon request" in {
        val idleTestKit = BehaviorTestKit(
          coordinator.LvCoordinator(
            cfg,
            inputDataProvider.ref,
            lvCoordinatorAdapter.ref
          )
        )
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

    "awaiting input data" should {
      "terminate if asked to do so" in {
        val awaitingTestKit = BehaviorTestKit(
          LvCoordinator invokePrivate PrivateMethod[Behavior[
            coordinator.Request
          ]](
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

        awaitingTestKit.run(coordinator.Terminate)

        awaitingTestKit.logEntries() should contain only CapturedLogEvent(
          Level.INFO,
          "Got request to terminate."
        )
        awaitingTestKit.isAlive shouldBe false
      }

      "terminate, if a request has been answered with a failure" in {
        val awaitingTestKit = BehaviorTestKit(
          LvCoordinator invokePrivate PrivateMethod[Behavior[
            coordinator.Request
          ]](
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
        awaitingTestKit.run(
          WrappedInputDataResponse(
            InputDataProvider.OsmReadFailed(
              new RuntimeException("Some random failure.")
            )
          )
        )

        awaitingTestKit.logEntries() should contain only CapturedLogEvent(
          Level.ERROR,
          "Request of needed input data failed. Stop low voltage grid generation.",
          Some(
            RequestFailedException(
              "The requested OSM data cannot be read. Stop generation."
            )
          ),
          None
        )
        awaitingTestKit.isAlive shouldBe false
      }

      val awaitingData = AwaitingData.empty(
        IdleData(
          cfg,
          inputDataProvider.ref,
          lvCoordinatorAdapter.ref,
          msgAdapters
        )
      )
      "spawn a child actor only if all data has arrived" in {
        val awaitingTestKit = BehaviorTestKit[Request](
          LvCoordinator invokePrivate PrivateMethod[Behavior[
            coordinator.Request
          ]](
            Symbol(
              "awaitInputData"
            )
          )(awaitingData)
        )
        val osmData = InputDataProvider.RepOsm(
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
            InputDataProvider.RepAssetTypes(
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
          case StartGeneration(lvConfig, _, _, _) => lvConfig shouldBe cfg
          case unexpected => fail(s"Received unexpected message '$unexpected'.")
        }
      }
    }

    "awaiting results" should {
      "terminate if asked to do so" in {
        val awaitingTestKit = BehaviorTestKit(
          LvCoordinator invokePrivate PrivateMethod[Behavior[
            coordinator.Request
          ]](Symbol("awaitResults"))(
            lvCoordinatorAdapter.ref,
            msgAdapters,
            ResultData.empty
          )
        )

        awaitingTestKit.run(coordinator.Terminate)

        awaitingTestKit.logEntries() should contain only CapturedLogEvent(
          Level.INFO,
          "Got request to terminate."
        )
        awaitingTestKit.isAlive shouldBe false
      }

      "properly perform generation" in {
        val awaitingTestKit = BehaviorTestKit[Request](
          LvCoordinator invokePrivate PrivateMethod[Behavior[
            coordinator.Request
          ]](Symbol("awaitResults"))(
            lvCoordinatorAdapter.ref,
            msgAdapters,
            ResultData.empty
          )
        )
        /* Mocking the lv region generator */
        val gridUuid = UUID.randomUUID()
        val mockedSubgrid = mock[SubGridContainer]
        val gridToExpect =
          LvRegionCoordinator.GridToExpect(gridUuid)
        val mockedBehavior: Behavior[LvRegionCoordinator.Request] =
          Behaviors.receive[LvRegionCoordinator.Request] { case (ctx, msg) =>
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
          asynchronousTestKit.createTestProbe[LvRegionCoordinator.Request]()
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
          StartGeneration(
            cfg,
            mockedLvRegionCoordinator,
            lvOsmoGridModel,
            assetInformation
          )
        )
        regionCoordinatorProbe
          .expectMessageType[LvRegionCoordinator.Partition] match {
          case Partition(
                osmoGridModel,
                assetInformation,
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
          .expectMessageType[LvRegionCoordinator.GridToExpect]
        regionResponse shouldBe gridToExpect
        awaitingTestKit.run(
          WrappedRegionResponse(regionResponse)
        ) // Forward from probe to actor

        /* The LvCoordinator now waits for the expected grid to be generated */
        val repLvGrid = RepLvGrid(gridUuid, mockedSubgrid)
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
