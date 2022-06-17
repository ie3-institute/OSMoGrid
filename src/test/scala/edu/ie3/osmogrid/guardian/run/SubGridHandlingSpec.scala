/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.guardian.run

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import edu.ie3.datamodel.models.input.NodeInput
import edu.ie3.datamodel.models.input.connector.ConnectorInput
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.osmogrid.cfg.OsmoGridConfigFactory
import edu.ie3.osmogrid.io.input.InputDataProvider
import edu.ie3.osmogrid.io.output.ResultListener
import edu.ie3.osmogrid.lv.coordinator
import edu.ie3.test.common.{GridSupport, UnitSpec}
import org.scalatest.BeforeAndAfterAll
import org.scalatestplus.mockito.MockitoSugar.mock
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.util.Try
import scala.jdk.CollectionConverters._

class SubGridHandlingSpec
    extends UnitSpec
    with GridSupport
    with BeforeAndAfterAll {
  private val testKit: ActorTestKit = ActorTestKit()

  "Supporting sub grid handling" when {
    "assigning sub grid numbers to a single sub grid container" should {
      val assignSubnetNumber =
        PrivateMethod[Try[SubGridContainer]](Symbol("assignSubnetNumber"))

      "return the same container with adapted sub grid number" in {
        Given("a simple subgrid with a small grid graph and a few participants")
        val subGridContainer = simpleSubGrid(111)
        val newSubnet = 42

        When("assigning a different subgrid number")
        val actual =
          (SubGridHandling invokePrivate assignSubnetNumber(
            subGridContainer,
            newSubnet
          )).success.get

        Then(
          "subnet number should be set in all nodes and new nodes should be linked"
        )
        actual.getSubnet shouldBe newSubnet
        val newNodes = actual.getRawGrid.getNodes.asScala
        newNodes.foreach(_.getSubnet shouldBe newSubnet)
        actual.getSystemParticipants
          .allEntitiesAsList()
          .asScala
          .foreach(participant => newNodes should contain(participant.getNode))
        checkNodes(actual.getRawGrid.getLines.asScala, newNodes)
        checkNodes(actual.getRawGrid.getTransformer2Ws.asScala, newNodes)
        checkNodes(actual.getRawGrid.getSwitches.asScala, newNodes)
      }
    }

    "assigning sub grid numbers to a series of sub grid containers" should {
      val assignSubnetNumbers =
        PrivateMethod[Try[Seq[SubGridContainer]]](Symbol("assignSubnetNumbers"))

      "return the same containers" in {
        /* ATTENTION: This is a dummy test until the concrete logic is implemented */
        val containers = Range(1, 10).map(_ => mock[SubGridContainer])

        val actual =
          SubGridHandling invokePrivate assignSubnetNumbers(containers)

        actual.success.get should contain theSameElementsAs containers
      }
    }

    "handling incoming results" when {
      implicit val log: Logger =
        LoggerFactory.getLogger("SubGridHandlingTestLogger")

      val inputDataProvider =
        testKit.createTestProbe[InputDataProvider.InputDataEvent](
          "InputDataProvider"
        )
      val lvCoordinatorAdapter =
        testKit.createTestProbe[coordinator.Response]("LvCoordinatorAdapter")
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
      val messageAdapters = new MessageAdapters(
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
          private val result = handleLvResults(
            grids,
            cfg.generation,
            Seq(resultListener.ref, additionalResultListener.ref),
            messageAdapters
          )

          result.isSuccess shouldBe true

          resultListener.receiveMessage() match {
            case ResultListener.GridResult(grid, _) =>
              grid.getGridName shouldBe "DummyGrid"
              grid.getRawGrid.getNodes.size() shouldBe 0
            case unexpected =>
              fail(s"Received unexpected message '$unexpected'.")
          }
          additionalResultListener.receiveMessage() match {
            case ResultListener.GridResult(grid, _) =>
              grid.getGridName shouldBe "DummyGrid"
              grid.getRawGrid.getNodes.size() shouldBe 0
            case unexpected =>
              fail(s"Received unexpected message '$unexpected'.")
          }
          inputDataProvider.expectNoMessage()
          lvCoordinatorAdapter.expectNoMessage()
          resultListenerAdapter.expectNoMessage()
        }
      }
    }
  }

  override protected def afterAll(): Unit = testKit.shutdownTestKit()

  private def checkNodes(
      actualInputs: Iterable[ConnectorInput],
      expectedNodes: Iterable[NodeInput]
  ): Unit =
    actualInputs.foreach { connectorInput =>
      expectedNodes should contain(connectorInput.getNodeA)
      expectedNodes should contain(connectorInput.getNodeB)
    }
}
