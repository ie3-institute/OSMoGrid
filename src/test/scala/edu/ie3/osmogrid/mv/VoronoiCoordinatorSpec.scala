/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.mv

import edu.ie3.osmogrid.cfg.OsmoGridConfigFactory
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.test.common.{GridSupport, MvTestData, UnitSpec}
import org.apache.pekko.actor.testkit.typed.CapturedLogEvent
import org.apache.pekko.actor.testkit.typed.scaladsl.{
  ActorTestKit,
  BehaviorTestKit,
  ScalaTestWithActorTestKit,
}
import org.scalatest.BeforeAndAfterAll
import org.slf4j.event.Level
import utils.GridConversion.NodeConversion

import scala.jdk.CollectionConverters._

class VoronoiCoordinatorSpec
    extends ScalaTestWithActorTestKit
    with UnitSpec
    with BeforeAndAfterAll
    with GridSupport
    with MvTestData {
  private val asynchronousTestKit = ActorTestKit()
  private val mvCoordinator =
    asynchronousTestKit.createTestProbe[MvRequest]("MvCoordinator")

  private val cfg =
    OsmoGridConfigFactory.defaultTestConfig.generation.mv.getOrElse(
      fail("Test config does not contain config for mv grid generation.")
    )

  "A voronoi coordinator being idle" should {
    "terminate if asked to do so" in {
      val idleTestKit = BehaviorTestKit(
        VoronoiCoordinator(mvCoordinator.ref)
      )
      idleTestKit.run(MvTerminate)

      idleTestKit.logEntries() should contain only CapturedLogEvent(
        Level.INFO,
        "Got request to terminate.",
      )
    }

    "log a warning if an unsupported message arrives" in {
      val idleTestKit = BehaviorTestKit(
        VoronoiCoordinator(mvCoordinator.ref)
      )
      idleTestKit.run(
        StartMvGraphConversion(
          1,
          new OsmGraph(),
          Some(nodeToHv.getUuid),
          new NodeConversion(Map.empty, Map.empty),
          assetInformation,
        )
      )

      idleTestKit.logEntries().exists { entry =>
        entry.message.contains(
          "Received unsupported message 'StartMvGraphConversion(1,([], []),Some(92c3a19d-2a07-4472-bd7a-cbb49a5ae5fd)"
        ) &&
        entry.level == Level.WARN
      } shouldBe true
    }
  }

  "A voronoi coordinator" should {
    val idleTestKit = BehaviorTestKit(
      VoronoiCoordinator(
        mvCoordinator.ref
      )
    )

    "create a graph correctly" in {
      idleTestKit.run(
        StartMvGraphGeneration(1, polygon, None, streetGraph, assetInformation)
      )
      idleTestKit.isAlive shouldBe true
    }

    "convert a graph correctly" in {
      val graph = new OsmGraph()
      graph.addVertex(transitionPoint)
      graph.addVertex(osmNode1)
      graph.addVertex(osmNode2)
      graph.addEdge(transitionPoint, osmNode1)
      graph.addEdge(transitionPoint, osmNode2)
      graph.addEdge(osmNode1, osmNode2)

      idleTestKit.run(
        StartMvGraphConversion(
          1,
          graph,
          Some(nodeToHv.getUuid),
          nodeConversion,
          assetInformation,
        )
      )

      idleTestKit
        .logEntries()
        .contains(
          CapturedLogEvent(
            Level.DEBUG,
            "Starting conversion for the graph of the grid 1.",
          )
        ) shouldBe true

      idleTestKit.isAlive shouldBe false
    }

    "send a finish message to the mv coordinator" in {
      val message = mvCoordinator.receiveMessage()

      message match {
        case WrappedMvResponse(
              FinishedMvGridData(subgrid, nodeChanges)
            ) =>
          subgrid.getSubnet shouldBe 1
          val nodes = subgrid.getRawGrid.getNodes.asScala
          val lines = subgrid.getRawGrid.getLines.asScala

          nodes.size shouldBe 3
          nodes.contains(nodeToHv) shouldBe true
          nodes.contains(nodeInMv1) shouldBe true
          nodes.contains(nodeInMv2) shouldBe true

          lines.size shouldBe 3
          val lineNodes = lines.map { l => l.allNodes().asScala.toSeq }
          lineNodes.contains(Seq(nodeToHv, nodeInMv1)) shouldBe true
          lineNodes.contains(Seq(nodeToHv, nodeInMv2)) shouldBe true
          lineNodes.contains(Seq(nodeInMv1, nodeInMv2)) shouldBe true

          nodeChanges.size shouldBe 3
          nodeChanges.values should contain allElementsOf Seq(
            nodeToHv,
            nodeInMv1,
            nodeInMv2,
          ).map { n => n.copy().subnet(1).build() }
        case other => fail(s"$other is not expected as a message!")
      }
    }
  }

  override protected def afterAll(): Unit = {
    asynchronousTestKit.shutdownTestKit()
    super.afterAll()
  }
}
