/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.guardian.run

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import edu.ie3.datamodel.models.input.NodeInput
import edu.ie3.datamodel.models.input.connector.`type`.Transformer3WTypeInput
import edu.ie3.datamodel.models.input.connector.{Transformer2WInput, Transformer3WInput}
import edu.ie3.datamodel.models.input.container.{JointGridContainer, SubGridContainer}
import edu.ie3.datamodel.models.voltagelevels.GermanVoltageLevelUtils._
import edu.ie3.datamodel.models.{StandardUnits, UniqueEntity}
import edu.ie3.osmogrid.cfg.OsmoGridConfigFactory
import edu.ie3.osmogrid.io.output.ResultListenerProtocol
import edu.ie3.osmogrid.lv.coordinator
import edu.ie3.osmogrid.mv.MvResponse
import edu.ie3.test.common.{GridSupport, UnitSpec}
import org.locationtech.jts.geom.Point
import org.scalatest.BeforeAndAfterAll
import org.scalatestplus.mockito.MockitoSugar.mock
import org.slf4j.{Logger, LoggerFactory}
import tech.units.indriya.quantity.Quantities
import utils.GridContainerUtils

import java.util.UUID
import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag
import scala.util.Try

class SubGridHandlingSpec
    extends UnitSpec
    with GridSupport
    with BeforeAndAfterAll {
  private val testKit: ActorTestKit = ActorTestKit()

  "Supporting sub grid handling" when {
    "update container correctly" in {
      val updateContainer =
        PrivateMethod[JointGridContainer](Symbol("updateContainer"))

      val lv1 = mockSubGrid(1, MV_10KV, LV)

      val jointGridContainer = new JointGridContainer(
        lv1.getGridName,
        lv1.getRawGrid,
        lv1.getSystemParticipants,
        lv1.getGraphics
      )

      // change the voltage level of the first found mv node to 20 kV
      val node = GridContainerUtils
        .filterLv(Seq(lv1))(0)
        .copy()
        .subnet(10)
        .voltLvl(MV_20KV)
        .build()

      val updated = SubGridHandling invokePrivate updateContainer(
        jointGridContainer,
        Seq(node),
        assetInformation
      )

      val updatesNodes = updated.getRawGrid.getNodes.asScala.toSeq
      updatesNodes.size shouldBe 2
      updatesNodes should contain(node)

      val updatedTransformer =
        updated.getRawGrid.getTransformer2Ws.asScala.toSeq
      updatedTransformer.size shouldBe 1

      updatedTransformer(0).getType shouldBe trafo_20kV_to_lv
    }

    "update transformer2W correctly" in {
      val updateTransformer2Ws = PrivateMethod[Try[Seq[Transformer2WInput]]](
        Symbol("updateTransformer2Ws")
      )

      val lv1 = mockSubGrid(1, MV_10KV, LV)
      val transformerType = assetInformation.transformerTypes.toSeq(0)

      // change the voltage level of the first found mv node to 20 kV
      val node = GridContainerUtils
        .filterLv(Seq(lv1))(0)
        .copy()
        .subnet(10)
        .voltLvl(MV_20KV)
        .build()

      val nodeMapping: Map[UUID, NodeInput] =
        lv1.getRawGrid.getNodes.asScala.map { n =>
          val id = n.getUuid

          if (id == node.getUuid) {
            id -> node
          } else {
            id -> n
          }
        }.toMap

      val updated: Try[Seq[Transformer2WInput]] =
        SubGridHandling invokePrivate updateTransformer2Ws(
          lv1.getRawGrid.getTransformer2Ws.asScala.toSeq,
          nodeMapping,
          Seq(transformerType)
        )

      updated.fold(
        _ => throw new Error("This test should pass!"),
        s =>
          s.headOption match {
            case Some(transformer) =>
              transformer.getId shouldBe "Dummy transformer"
              transformer.getNodeA shouldBe node
              transformer.getParallelDevices shouldBe 1
              transformer.getType shouldBe transformerType
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

      // include at least a single node for voltage level determination
      val dummyNodeA = new NodeInput(
        UUID.randomUUID(),
        s"Dummy node in 10",
        Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
        true,
        mock[Point],
        MV_10KV,
        10
      )

      val dummyNodeB = new NodeInput(
        UUID.randomUUID(),
        s"Dummy node in 10",
        Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
        false,
        mock[Point],
        MV_20KV,
        20
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
        mock[Transformer3WTypeInput],
        0,
        false
      )

      // change the voltage level of the first found mv node to 20 kV
      val node = dummyNodeA
        .copy()
        .subnet(10)
        .voltLvl(MV_20KV)
        .build()

      val nodeMapping: Map[UUID, NodeInput] = Map(
        dummyNodeA.getUuid -> node,
        dummyNodeB.getUuid -> dummyNodeB,
        dummyNodeC.getUuid -> dummyNodeC
      )

      val updated: Try[Seq[Transformer3WInput]] =
        SubGridHandling invokePrivate updateTransformer3Ws(
          Seq(dummyTrafo),
          nodeMapping,
          Seq(dummyTransformer3WType)
        )

      updated.fold(
        _ => throw new Error("This test should pass!"),
        s =>
          s.headOption match {
            case Some(transformer) =>
              transformer.getId shouldBe "Dummy transformer"
              transformer.getNodeA shouldBe node
              transformer.getNodeB shouldBe dummyNodeB
              transformer.getNodeC shouldBe dummyNodeC
              transformer.getParallelDevices shouldBe 1
              transformer.getType shouldBe dummyTransformer3WType
              transformer.getTapPos shouldBe 0
              transformer.isAutoTap shouldBe false
            case None => throw new Error("This test should pass!")
          }
      )

    }

    "assigning sub grid numbers to a single sub grid container" should {
      val assignSubnetNumber =
        PrivateMethod[Try[SubGridContainer]](Symbol("assignSubnetNumber"))

      "return the same container with adapted nodes and sub grid number" in {
        Given("a simple subgrid with a small grid graph and a few participants")
        val givenContainer = simpleSubGrid(111)
        val newSubnet = 42

        When("assigning a different subgrid number")
        val actualContainer =
          (SubGridHandling invokePrivate assignSubnetNumber(
            givenContainer,
            newSubnet
          )).success.get

        Then(
          "subnet number should be set in all nodes and new nodes should be linked"
        )
        checkSubgridContainer(givenContainer, actualContainer, newSubnet)
      }
    }

    "assigning sub grid numbers to a series of sub grid containers" should {
      val assignSubnetNumbers =
        PrivateMethod[Try[Seq[SubGridContainer]]](Symbol("assignSubnetNumbers"))

      "return the same containers with adapted nodes and sub grid numbers" in {
        val givenContainers = Range.inclusive(42, 52).map(simpleSubGrid)

        val actual =
          SubGridHandling invokePrivate assignSubnetNumbers(givenContainers)
        val actualContainers = actual.success.get

        actualContainers.size shouldBe givenContainers.size

        givenContainers.zip(actualContainers).zipWithIndex.foreach {
          case ((given, actual), subnetNo) =>
            checkSubgridContainer(given, actual, subnetNo + 1)
        }
      }
    }

    "handling incoming results" when {
      implicit val log: Logger =
        LoggerFactory.getLogger("SubGridHandlingTestLogger")

      val lvCoordinatorAdapter =
        testKit.createTestProbe[coordinator.Response]("LvCoordinatorAdapter")
      val mvCoordinatorAdapter =
        testKit.createTestProbe[MvResponse]("MvCoordinatorAdapter")
      val resultListener =
        testKit.createTestProbe[ResultListenerProtocol.Request](
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

  /** Checks the actual [[SubGridContainer]] and compares it to the given one
    * regarding node mapping. Besides nodes and sub grid numbers, no other
    * parameters are compared here: It is assumed that entities with the same
    * UUID have the same properties besides the mentioned ones.
    * @param given
    *   the [[SubGridContainer]] that was given for the test
    * @param actual
    *   the [[SubGridContainer]] that was computed during the test
    * @param expectedSubgridNo
    *   the subgrid number that is expected for the computed sub grid
    */
  private def checkSubgridContainer(
      `given`: SubGridContainer,
      actual: SubGridContainer,
      expectedSubgridNo: Int
  ): Unit = {
    actual.getSubnet shouldBe expectedSubgridNo

    // CHECK NODES
    actual.getRawGrid.getNodes.size shouldBe given.getRawGrid.getNodes.size()
    actual.getRawGrid.getNodes.asScala
      .foreach(_.getSubnet shouldBe expectedSubgridNo)

    val givenToActualNodes = createMapByUUID(
      given.getRawGrid.getNodes.asScala,
      actual.getRawGrid.getNodes.asScala
    )

    // compare nodes before and after
    given.getRawGrid.getNodes.asScala
      .map { givenNode =>
        (givenNode, givenToActualNodes.get(givenNode).value)
      }
      .foreach { case (given, actual) =>
        actual shouldBe given.copy().subnet(actual.getSubnet).build()
      }

    // CHECK OTHER GRID ELEMENTS
    (
      createMapByUUID(
        given.getRawGrid.getLines.asScala,
        actual.getRawGrid.getLines.asScala
      ).toSeq ++
        createMapByUUID(
          given.getRawGrid.getSwitches.asScala,
          actual.getRawGrid.getSwitches.asScala
        ).toSeq ++
        createMapByUUID(
          given.getRawGrid.getTransformer2Ws.asScala,
          actual.getRawGrid.getTransformer2Ws.asScala
        ).toSeq
    ).foreach { case (given, actual) =>
      checkNode(given.getNodeA, actual.getNodeA, givenToActualNodes)
      checkNode(given.getNodeB, actual.getNodeB, givenToActualNodes)
    }

    createMapByUUID(
      given.getRawGrid.getTransformer3Ws.asScala,
      actual.getRawGrid.getTransformer3Ws.asScala
    ).toSeq.foreach { case (given, actual) =>
      checkNode(given.getNodeA, actual.getNodeA, givenToActualNodes)
      checkNode(given.getNodeB, actual.getNodeB, givenToActualNodes)
      checkNode(given.getNodeC, actual.getNodeC, givenToActualNodes)
    }

    createMapByUUID(
      given.getRawGrid.getMeasurementUnits.asScala,
      actual.getRawGrid.getMeasurementUnits.asScala
    ).toSeq.foreach { case (given, actual) =>
      checkNode(given.getNode, actual.getNode, givenToActualNodes)
    }

    // CHECK SYSTEM PARTICIPANTS
    createMapByUUID(
      given.getSystemParticipants.allEntitiesAsList.asScala,
      actual.getSystemParticipants.allEntitiesAsList.asScala
    ).toSeq.foreach { case (given, actual) =>
      checkNode(given.getNode, actual.getNode, givenToActualNodes)
    }
  }

  private def checkNode(
      givenNode: NodeInput,
      actualNode: NodeInput,
      givenToActualNodes: Map[NodeInput, NodeInput]
  ): Unit = {
    val expectedNode = givenToActualNodes.getOrElse(
      givenNode,
      throw new RuntimeException(
        s"Actual node with UUID ${givenNode.getUuid} not found."
      )
    )
    actualNode shouldBe expectedNode
  }

  private def createMapByUUID[T <: UniqueEntity](
      `given`: Iterable[T],
      actual: Iterable[T]
  )(implicit tag: ClassTag[T]): Map[T, T] = {
    val actualByUUID = actual.map { actualEntity =>
      actualEntity.getUuid -> actualEntity
    }.toMap

    given.map { givenEntity =>
      givenEntity -> actualByUUID.getOrElse(
        givenEntity.getUuid,
        throw new RuntimeException(
          s"Actual ${tag.runtimeClass.getSimpleName} with UUID ${givenEntity.getUuid} not found."
        )
      )
    }.toMap
  }
}
