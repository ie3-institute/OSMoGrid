/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.guardian.run

import akka.actor.testkit.typed.Effect.MessageAdapter
import akka.actor.testkit.typed.scaladsl.{ActorTestKit, TestProbe}
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.osmogrid.cfg.{OsmoGridConfig, OsmoGridConfigFactory}
import edu.ie3.osmogrid.guardian.run.{RunGuardian, SubGridHandling}
import edu.ie3.osmogrid.io.input.InputDataProvider
import edu.ie3.osmogrid.io.output.ResultListener
import edu.ie3.osmogrid.lv.LvCoordinator
import edu.ie3.test.common.{GridSupport, UnitSpec}
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
      val messageAdapters = new RunGuardian.MessageAdapters(
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
        "inform the right parties about correct information" in new SubGridHandling {
          handleLvResults(
            grids,
            cfg.generation,
            Seq(resultListener.ref, additionalResultListener.ref),
            messageAdapters
          )

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
    }
  }

  override protected def afterAll(): Unit = testKit.shutdownTestKit()
}
