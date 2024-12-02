/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.guardian.run

import edu.ie3.datamodel.models.StandardUnits
import edu.ie3.datamodel.models.input.connector.{
  Transformer2WInput,
  Transformer3WInput,
}
import edu.ie3.datamodel.models.input.container.{
  GraphicElements,
  RawGridElements,
  SubGridContainer,
  SystemParticipants,
}
import edu.ie3.datamodel.models.input.graphics.GraphicInput
import edu.ie3.datamodel.models.input.system.SystemParticipantInput
import edu.ie3.datamodel.models.input.{AssetInput, NodeInput}
import edu.ie3.datamodel.models.voltagelevels.GermanVoltageLevelUtils._
import edu.ie3.osmogrid.cfg.{OsmoGridConfig, OsmoGridConfigFactory}
import edu.ie3.osmogrid.exception.GridException
import edu.ie3.osmogrid.io.output.{OutputRequest, ResultListenerProtocol}
import edu.ie3.osmogrid.lv.LvResponse
import edu.ie3.osmogrid.mv.MvResponse
import edu.ie3.test.common.{GridSupport, UnitSpec}
import org.apache.pekko.actor.testkit.typed.scaladsl.ActorTestKit
import org.locationtech.jts.geom.Point
import org.scalatest.BeforeAndAfterAll
import org.scalatestplus.mockito.MockitoSugar.mock
import org.slf4j.{Logger, LoggerFactory}
import tech.units.indriya.quantity.Quantities

import java.util.UUID
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

class SubGridHandlingSpec
    extends UnitSpec
    with GridSupport
    with BeforeAndAfterAll
    with SubGridHandling {
  private val testKit: ActorTestKit = ActorTestKit()

  "The SubGridHandling" should {
    val resultListener = testKit.createTestProbe[ResultListenerProtocol]()
    val lvCoordinator = testKit.createTestProbe[LvResponse]()
    val mvCoordinator = testKit.createTestProbe[MvResponse]()

    val listener = Seq(resultListener.ref)
    val msgAdapters: MessageAdapters = MessageAdapters(
      lvCoordinator.ref,
      mvCoordinator.ref,
    )
    val log = testKit.system.log
    val cfg = OsmoGridConfig.Grids.Output(hv = true, lv = true, mv = true)

    "handle empty results correctly" in {
      val empty = Try {
        handleResults(
          cfg,
          None,
          None,
          None,
          None,
          None,
          listener,
          msgAdapters,
        )(log)
      }

      empty match {
        case Failure(exception: GridException) =>
          exception.msg shouldBe "Error during creating of joint grid container, because no grids were found."
        case Success(value) =>
          throw new Error(
            s"This test should not pass! But received values: $value"
          )
      }
    }

    "process lv results correctly" in {
      val lv = mockSubGrid(1, MV_10KV, LV)

      val processed = processResults(
        cfg,
        Some(Seq(lv)),
        None,
        None,
        None,
        Some(assetInformation),
      )

      processed.size shouldBe 1
      val grid = processed(0)

      val rawGridElements = grid.getRawGrid
      val numbers = rawGridElements.getNodes.asScala.toSeq.map(_.getSubnet)
      numbers.count(_ == 1) shouldBe 2
      numbers.count(_ == 3) shouldBe 1

      val transformer = rawGridElements.getTransformer2Ws.asScala.toSeq(0)
      transformer.getNodeA.getSubnet shouldBe 3
      transformer.getNodeB.getSubnet shouldBe 1

      grid.getSystemParticipants shouldBe lv.getSystemParticipants
      grid.getGraphics shouldBe lv.getGraphics
    }

    "process and update lv results correctly" in {
      val lv = mockSubGrid(1, MV_10KV, LV)

      val commonNode = lv.getRawGrid.getNodes.asScala
        .filter(_.isSlack)
        .toSeq(0)
        .copy()
        .slack(false)
        .build()

      val mv = new SubGridContainer(
        "3",
        3,
        new RawGridElements(List[AssetInput](commonNode).asJava),
        new SystemParticipants(List.empty[SystemParticipantInput].asJava),
        new GraphicElements(List.empty[GraphicInput].asJava),
      )

      val expectedUpdatedNode = commonNode.copy().subnet(3).build()

      val processed = processResults(
        cfg,
        Some(Seq(lv)),
        Some(Seq(mv)),
        None,
        None,
        Some(assetInformation),
      )

      processed.size shouldBe 2
      val grid = processed(0)

      val rawGridElements = grid.getRawGrid
      rawGridElements.getNodes.asScala should contain(expectedUpdatedNode)
      rawGridElements.getTransformer2Ws.asScala
        .toSeq(0)
        .getNodeA shouldBe expectedUpdatedNode

      grid.getSystemParticipants shouldBe lv.getSystemParticipants
      grid.getGraphics shouldBe lv.getGraphics
    }
  }

  "Supporting sub grid handling" when {

    "update transformer2W correctly" in {
      val updateTransformer2Ws = PrivateMethod[Try[Seq[Transformer2WInput]]](
        Symbol("updateTransformer2Ws")
      )

      val dummyNodeA = new NodeInput(
        UUID.randomUUID(),
        s"Dummy node in 10",
        Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
        false,
        mock[Point],
        MV_20KV,
        10,
      )
      val dummyNodeB = new NodeInput(
        UUID.randomUUID(),
        s"Dummy node in 1",
        Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
        false,
        mock[Point],
        LV,
        1,
      )

      val dummyTrafo = new Transformer2WInput(
        UUID.randomUUID(),
        s"Dummy transformer",
        dummyNodeA,
        dummyNodeB,
        1,
        trafo_10kV_to_lv,
        0,
        false,
      )

      val updated: Try[Seq[Transformer2WInput]] =
        SubGridHandling invokePrivate updateTransformer2Ws(
          Seq(dummyTrafo),
          assetInformation.transformerTypes,
        )

      updated.getOrElse(fail("This test should pass!")).headOption match {
        case Some(transformer) =>
          transformer.getId shouldBe "Dummy transformer"
          transformer.getNodeA shouldBe dummyNodeA
          transformer.getNodeB shouldBe dummyNodeB
          transformer.getParallelDevices shouldBe 1
          transformer.getType shouldBe trafo_20kV_to_lv
          transformer.getTapPos shouldBe 0
          transformer.isAutoTap shouldBe false
        case None => fail("This test should pass!")
      }
    }

    "update transformer3W correctly" in {
      val updateTransformer3Ws = PrivateMethod[Try[Seq[Transformer3WInput]]](
        Symbol("updateTransformer3Ws")
      )

      val dummyNodeA = new NodeInput(
        UUID.randomUUID(),
        s"Dummy node in 20",
        Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
        true,
        mock[Point],
        MV_20KV,
        20,
      )
      val dummyNodeB = new NodeInput(
        UUID.randomUUID(),
        s"Dummy node in 10",
        Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
        false,
        mock[Point],
        MV_10KV,
        10,
      )
      val dummyNodeC = new NodeInput(
        UUID.randomUUID(),
        s"Dummy node in 1",
        Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
        false,
        mock[Point],
        LV,
        1,
      )

      val dummyTrafo = new Transformer3WInput(
        UUID.randomUUID(),
        s"Dummy transformer",
        dummyNodeA,
        dummyNodeB,
        dummyNodeC,
        1,
        trafo_HV_10kV_LV,
        0,
        false,
      )

      val updated: Try[Seq[Transformer3WInput]] =
        SubGridHandling invokePrivate updateTransformer3Ws(
          Seq(dummyTrafo),
          Seq(trafo_20kV_10kV_LV),
        )

      updated.getOrElse(fail("This test should pass!")).headOption match {
        case Some(transformer) =>
          transformer.getId shouldBe "Dummy transformer"
          transformer.getNodeA shouldBe dummyNodeA
          transformer.getNodeB shouldBe dummyNodeB
          transformer.getNodeC shouldBe dummyNodeC
          transformer.getParallelDevices shouldBe 1
          transformer.getType shouldBe trafo_20kV_10kV_LV
          transformer.getTapPos shouldBe 0
          transformer.isAutoTap shouldBe false
        case None => fail("This test should pass!")
      }

    }

    "assigning sub grid numbers to a single sub grid containers nodes" should {
      val assignSubNetNumbers =
        PrivateMethod[(Map[UUID, NodeInput], Int)](
          Symbol("assignSubNetNumbers")
        )

      "return adapted nodes" in {
        Given("a simple subgrid with a small grid graph and a few participants")
        val givenContainer = simpleSubGrid(111)
        val newSubnet: Int = 42

        When("assigning a different subgrid number")
        val actual = SubGridHandling invokePrivate assignSubNetNumbers(
          Seq(givenContainer),
          newSubnet,
        )

        Then(
          "subnet number should be set in all nodes and new nodes should be linked"
        )

        val allNodes = actual._1.values.toSet

        allNodes
          .filter(!_.getId.contains("Top node"))
          .map(_.getSubnet) shouldBe Set(42)

        allNodes
          .filter(_.getId.contains("Top node"))
          .map(_.getSubnet) shouldBe Set(44)
      }
    }

    "assigning sub grid numbers to a series of sub grid containers nodes" should {
      val assignSubNetNumbers =
        PrivateMethod[(Map[UUID, NodeInput], Int)](
          Symbol("assignSubNetNumbers")
        )

      "return the adapted nodes" in {
        val givenContainers = Range.inclusive(42, 52).map(simpleSubGrid)

        val actual = SubGridHandling invokePrivate assignSubNetNumbers(
          givenContainers,
          42,
        )

        actual._1.values.size shouldBe givenContainers.size * 5

        for (i <- 42 to 52) {
          actual._1.values
            .filter(!_.getId.contains("Top"))
            .count(_.getSubnet == i) shouldBe 3
        }

        val topNodes = actual._1.values.filter(_.getId.contains("Top"))
        topNodes.size shouldBe 22
        topNodes.map(_.getSubnet).toSet shouldBe Set(54)
      }
    }

    "handling incoming results" when {
      implicit val log: Logger =
        LoggerFactory.getLogger("SubGridHandlingTestLogger")

      val lvCoordinatorAdapter =
        testKit.createTestProbe[LvResponse]("LvCoordinatorAdapter")
      val mvCoordinatorAdapter =
        testKit.createTestProbe[MvResponse]("MvCoordinatorAdapter")
      val resultListener =
        testKit.createTestProbe[OutputRequest](
          "ResultListener"
        )

      val grids = Range.inclusive(11, 20).map(mockSubGrid)
      val messageAdapters = new MessageAdapters(
        lvCoordinatorAdapter.ref,
        mvCoordinatorAdapter.ref,
      )
      val cfg = OsmoGridConfigFactory
        .parse {
          """
            |input.osm.file.pbf=test.pbf
            |input.asset.file.directory=assets/
            |output.csv.directory=output/
            |generation.lv.distinctHouseConnections=true
            |generation.mv.spawnMissingHvNodes = false
            |generation.mv.voltageLevel.id = mv
            |generation.mv.voltageLevel.default = 10.0""".stripMargin
        }
        .success
        .get

    }
  }

  override protected def afterAll(): Unit = testKit.shutdownTestKit()
}
