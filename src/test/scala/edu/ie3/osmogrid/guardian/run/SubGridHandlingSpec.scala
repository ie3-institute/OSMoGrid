/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.guardian.run

import org.apache.pekko.actor.testkit.typed.scaladsl.ActorTestKit
import edu.ie3.datamodel.models.UniqueEntity
import edu.ie3.datamodel.models.input.NodeInput
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.osmogrid.cfg.OsmoGridConfigFactory
import edu.ie3.osmogrid.io.output.{ResultListener, ResultListenerProtocol}
import edu.ie3.osmogrid.lv.coordinator
import edu.ie3.test.common.{GridSupport, UnitSpec}
import org.scalatest.BeforeAndAfterAll
import org.slf4j.{Logger, LoggerFactory}

import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag
import scala.util.Try

class SubGridHandlingSpec
    extends UnitSpec
    with GridSupport
    with BeforeAndAfterAll {
  private val testKit: ActorTestKit = ActorTestKit()

  "Supporting sub grid handling" when {
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
      val resultListener =
        testKit.createTestProbe[ResultListenerProtocol.Request](
          "ResultListener"
        )

      val grids = Range.inclusive(11, 20).map(mockSubGrid)
      val messageAdapters = new MessageAdapters(
        lvCoordinatorAdapter.ref
      )
      val cfg = OsmoGridConfigFactory
        .parse {
          """
          |input.osm.file.pbf=test.pbf
          |input.asset.file.directory=assets/
          |output.csv.directory=output/
          |generation.lv.distinctHouseConnections=true""".stripMargin
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
