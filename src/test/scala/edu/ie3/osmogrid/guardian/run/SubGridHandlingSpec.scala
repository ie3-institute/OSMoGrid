/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.guardian.run

import edu.ie3.datamodel.models.input.connector.{
  Transformer2WInput,
  Transformer3WInput
}
import edu.ie3.datamodel.models.input.container.{
  GraphicElements,
  RawGridElements,
  SubGridContainer,
  SystemParticipants
}
import edu.ie3.datamodel.models.input.graphics.GraphicInput
import edu.ie3.datamodel.models.input.system.SystemParticipantInput
import edu.ie3.datamodel.models.input.{AssetInput, NodeInput}
import edu.ie3.datamodel.models.voltagelevels.GermanVoltageLevelUtils
import edu.ie3.datamodel.models.voltagelevels.GermanVoltageLevelUtils._
import edu.ie3.datamodel.models.{StandardUnits, UniqueEntity}
import edu.ie3.osmogrid.cfg.OsmoGridConfigFactory
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
import scala.collection.immutable.Seq
import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag
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
      mvCoordinator.ref
    )
    val log = testKit.system.log

    "handle empty results correctly" in {
      val empty = Try {
        handleResults(
          None,
          None,
          None,
          assetInformation,
          listener,
          msgAdapters
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
        Some(Seq(lv)),
        None,
        None,
        assetInformation
      )

      val jointGrid = processed.getOrElse(fail("Expected a joint grid!"))

      val rawGridElements = jointGrid.getRawGrid
      rawGridElements.getNodes.asScala
        .filter(_.getId == "Dummy A node in 1")
        .map { node => node.getSubnet shouldBe 2 }
      rawGridElements.getTransformer2Ws.asScala
        .toSeq(0)
        .getNodeA
        .getSubnet shouldBe 2

      jointGrid.getSystemParticipants shouldBe lv.getSystemParticipants
      jointGrid.getGraphics shouldBe lv.getGraphics
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
        "2",
        2,
        new RawGridElements(List[AssetInput](commonNode).asJava),
        new SystemParticipants(List.empty[SystemParticipantInput].asJava),
        new GraphicElements(List.empty[GraphicInput].asJava)
      )

      val expectedUpdatedNode = commonNode.copy().subnet(2).build()

      val processed = processResults(
        Some(Seq(lv)),
        Some(Seq(mv)),
        None,
        assetInformation
      )

      val jointGrid = processed.getOrElse(fail("Expected a joint grid!"))

      val rawGridElements = jointGrid.getRawGrid
      rawGridElements.getNodes.asScala should contain(expectedUpdatedNode)
      rawGridElements.getTransformer2Ws.asScala
        .toSeq(0)
        .getNodeA shouldBe expectedUpdatedNode

      jointGrid.getSystemParticipants shouldBe lv.getSystemParticipants
      jointGrid.getGraphics shouldBe lv.getGraphics
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
        10
      )
      val dummyNodeB = new NodeInput(
        UUID.randomUUID(),
        s"Dummy node in 1",
        Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
        false,
        mock[Point],
        LV,
        1
      )

      val dummyTrafo = new Transformer2WInput(
        UUID.randomUUID(),
        s"Dummy transformer",
        dummyNodeA,
        dummyNodeB,
        1,
        trafo_10kV_to_lv,
        0,
        false
      )

      val updated: Try[Seq[Transformer2WInput]] =
        SubGridHandling invokePrivate updateTransformer2Ws(
          Seq(dummyTrafo),
          assetInformation.transformerTypes
        )

      updated.fold(
        _ => throw new Error("This test should pass!"),
        s =>
          s.headOption match {
            case Some(transformer) =>
              transformer.getId shouldBe "Dummy transformer"
              transformer.getNodeA shouldBe dummyNodeA
              transformer.getNodeB shouldBe dummyNodeB
              transformer.getParallelDevices shouldBe 1
              transformer.getType shouldBe trafo_20kV_to_lv
              transformer.getTapPos shouldBe 0
              transformer.isAutoTap shouldBe false
            case None => throw new Error("This test should pass!")
          }
      )
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
        20
      )
      val dummyNodeB = new NodeInput(
        UUID.randomUUID(),
        s"Dummy node in 10",
        Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
        false,
        mock[Point],
        MV_10KV,
        10
      )
      val dummyNodeC = new NodeInput(
        UUID.randomUUID(),
        s"Dummy node in 1",
        Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
        false,
        mock[Point],
        LV,
        1
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
        false
      )

      val updated: Try[Seq[Transformer3WInput]] =
        SubGridHandling invokePrivate updateTransformer3Ws(
          Seq(dummyTrafo),
          Seq(trafo_20kV_10kV_LV)
        )

      updated.fold(
        _ => throw new Error("This test should pass!"),
        s =>
          s.headOption match {
            case Some(transformer) =>
              transformer.getId shouldBe "Dummy transformer"
              transformer.getNodeA shouldBe dummyNodeA
              transformer.getNodeB shouldBe dummyNodeB
              transformer.getNodeC shouldBe dummyNodeC
              transformer.getParallelDevices shouldBe 1
              transformer.getType shouldBe trafo_20kV_10kV_LV
              transformer.getTapPos shouldBe 0
              transformer.isAutoTap shouldBe false
            case None => throw new Error("This test should pass!")
          }
      )

    }

    "assigning sub grid numbers to a single sub grid containers nodes" should {
      val assignSubgridNumbers =
        PrivateMethod[Map[UUID, NodeInput]](Symbol("assignSubgridNumbers"))

      "return adapted nodes" in {
        Given("a simple subgrid with a small grid graph and a few participants")
        val givenContainer = simpleSubGrid(111)
        val newSubnet: Int = 42

        When("assigning a different subgrid number")
        val actual = SubGridHandling invokePrivate assignSubgridNumbers(
          Seq(givenContainer),
          newSubnet
        )

        Then(
          "subnet number should be set in all nodes and new nodes should be linked"
        )

        val givenNodes = givenContainer.getRawGrid.getNodes.asScala.map {
          node => node.getUuid -> node
        }.toMap
        val lvNodes: Map[UUID, NodeInput] =
          givenNodes.filter(_._2.getVoltLvl == GermanVoltageLevelUtils.LV)

        actual.values.foreach { node =>
          if (lvNodes.contains(node.getUuid)) {
            if (node.getSubnet != 42) {
              fail(
                s"Expected 42 as subgrid number, but got ${node.getSubnet} instead!"
              )
            }
          } else {
            if (node.getSubnet != 43) {
              fail(
                s"Expected 43 as subgrid number, but got ${node.getSubnet} instead!"
              )
            }
          }
        }
      }
    }

    "assigning sub grid numbers to a series of sub grid containers nodes" should {
      val assignSubgridNumbers =
        PrivateMethod[Map[UUID, NodeInput]](Symbol("assignSubgridNumbers"))

      "return the adapted nodes" in {
        val givenContainers = Range.inclusive(42, 52).map(simpleSubGrid)

        val actual = SubGridHandling invokePrivate assignSubgridNumbers(
          givenContainers,
          42
        )
        actual.size shouldBe givenContainers.size * 5

        val topNodes = actual.values.filter(_.getId.contains("Top"))
        topNodes.size shouldBe 22
        topNodes.foreach(node => node.getSubnet shouldBe 53)

        val lvNodes =
          actual.filter(_._2.getVoltLvl == GermanVoltageLevelUtils.LV)
        lvNodes.size shouldBe 33

        givenContainers.zipWithIndex.map { case (grid, index) =>
          val uuids = grid.getRawGrid.getNodes.asScala
            .filter(_.getVoltLvl == GermanVoltageLevelUtils.LV)
            .map(_.getUuid)

          uuids.foreach { uuid => lvNodes(uuid).getSubnet shouldBe index + 42 }
        }
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
        mvCoordinatorAdapter.ref
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
