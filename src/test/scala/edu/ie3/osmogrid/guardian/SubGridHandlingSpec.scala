/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.guardian

import akka.actor.testkit.typed.Effect.MessageAdapter
import akka.actor.testkit.typed.scaladsl.{ActorTestKit, TestProbe}
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.osmogrid.cfg.{OsmoGridConfig, OsmoGridConfigFactory}
import edu.ie3.osmogrid.guardian.OsmoGridGuardian.{
  GuardianData,
  MessageAdapters,
  RunData
}
import edu.ie3.osmogrid.guardian.SubGridHandling
import edu.ie3.osmogrid.io.input.InputDataProvider
import edu.ie3.osmogrid.io.output.ResultListener
import edu.ie3.osmogrid.lv.LvCoordinator
import edu.ie3.test.common.{GridSupport, UnitSpec}
import org.influxdb.annotation.Measurement
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterAll
import org.scalatest.PrivateMethodTester.PrivateMethod
import org.scalatestplus.mockito.MockitoSugar.mock
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.jdk.CollectionConverters.*

class SubGridHandlingSpec
    extends UnitSpec
    with GridSupport
    with BeforeAndAfterAll {
  private val testKit: ActorTestKit = ActorTestKit()

  "Supporting sub grid handling" when {
    "assigning sub grid numbers to a single sub grid container" should {
      val assignSubnetNumber =
        PrivateMethod[SubGridContainer](Symbol("assignSubnetNumber"))

      "return the same container" in {
        /* ATTENTION: This is a dummy test until the concrete logic is implemented */
        val subGridContainer = mock[SubGridContainer]

        val actual =
          SubGridHandling invokePrivate assignSubnetNumber(subGridContainer, 42)

        actual shouldBe subGridContainer
      }
    }

    "assigning sub grid numbers to a series of sub grid containers" should {
      val assignSubnetNumbers =
        PrivateMethod[Seq[SubGridContainer]](Symbol("assignSubnetNumbers"))

      "return the same containers" in {
        /* ATTENTION: This is a dummy test until the concrete logic is implemented */
        val containers = Range(1, 10).map(_ => mock[SubGridContainer])

        val actual =
          SubGridHandling invokePrivate assignSubnetNumbers(containers)

        actual should contain theSameElementsAs containers
      }
    }

    "handling incoming results" when {
      implicit val log: Logger =
        LoggerFactory.getLogger("SubGridHandlingTestLogger")

      val inputDataProvider =
        testKit.createTestProbe[InputDataProvider.Request]("InputDataProvider")
      val lvCoordinatorAdapter =
        testKit.createTestProbe[LvCoordinator.Response]("LvCoordinatorAdapter")
      val resultListener =
        testKit.createTestProbe[ResultListener.ResultEvent]("ResultListener")
      val resultListenerAdapter =
        testKit.createTestProbe[ResultListener.Response](
          "ResultListenerAdapter"
        )
      val additionalResultListener =
        testKit.createTestProbe[ResultListener.ResultEvent](
          "AdditionalResultListener"
        )

      val runId = UUID.randomUUID()
      val grids = Range(1, 10).map(mockSubGrid)
      val messageAdapters = new OsmoGridGuardian.MessageAdapters(
        lvCoordinatorAdapter.ref,
        resultListenerAdapter.ref
      )
      val cfg = OsmoGridConfigFactory.parse {
        """
          |input.osm.file.pbf=test.pbf
          |input.asset.file.directory=assets/
          |output.csv.directory=output/
          |generation.lv.distinctHouseConnections=true""".stripMargin
      }.get

      "having an active run" should {
        val guardianData = GuardianData(
          messageAdapters,
          Map(
            runId -> RunData.Running(
              runId,
              cfg,
              Some(resultListener.ref),
              Seq(additionalResultListener.ref),
              inputDataProvider.ref
            )
          )
        )
        "inform the right parties about correct information" in new SubGridHandling {
          handleLvResults(runId, grids, guardianData)

          resultListener.receiveMessage() match {
            case ResultListener.GridResult(grid, _) =>
              grid.getGridName shouldBe "DummyGrid"
              grid.getRawGrid.getNodes.size() shouldBe 0
          }
          additionalResultListener.receiveMessage() match {
            case ResultListener.GridResult(grid, _) =>
              grid.getGridName shouldBe "DummyGrid"
              grid.getRawGrid.getNodes.size() shouldBe 0
          }
          inputDataProvider.expectNoMessage()
          lvCoordinatorAdapter.expectNoMessage()
          resultListenerAdapter.expectNoMessage()
        }
      }

      "having a run in coordinated shutdown phase" should {
        val guardianData = GuardianData(
          messageAdapters,
          Map(
            runId -> RunData.Stopping(
              runId,
              cfg
            )
          )
        )
        "inform nobody about anything" in new SubGridHandling {
          resultListener.expectNoMessage()
          additionalResultListener.expectNoMessage()
          inputDataProvider.expectNoMessage()
          lvCoordinatorAdapter.expectNoMessage()
          resultListenerAdapter.expectNoMessage()
        }
      }

      "having no matching run" should {
        val guardianData = GuardianData(
          messageAdapters,
          Map.empty[UUID, RunData]
        )
        "inform nobody about anything" in new SubGridHandling {
          resultListener.expectNoMessage()
          additionalResultListener.expectNoMessage()
          inputDataProvider.expectNoMessage()
          lvCoordinatorAdapter.expectNoMessage()
          resultListenerAdapter.expectNoMessage()
        }
      }
    }
  }

  override protected def afterAll(): Unit = testKit.shutdownTestKit()
}
