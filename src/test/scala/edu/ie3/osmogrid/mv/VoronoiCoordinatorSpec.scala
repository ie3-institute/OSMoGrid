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
  ScalaTestWithActorTestKit
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
        "Got request to terminate."
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
          assetInformation
        )
      )

      idleTestKit.logEntries() should contain only CapturedLogEvent(
        Level.WARN,
        "Received unsupported message 'StartMvGraphConversion(1,([], []),Some(92c3a19d-2a07-4472-bd7a-cbb49a5ae5fd),NodeConversion(Map(),Map()),AssetInformation(List(LineTypeInput{uuid=6b223bc3-69e2-4eb8-a2c0-76be1cd2c998, id=NA2XS2Y 1x400 RM/25 6/10 kV, b=1.6964599999999997E8 µS/km, g=0.0 µS/km, r=0.078 Ω/km, x=0.0942 Ω/km, iMax=535 A, vRated=10 kV}),List(Transformer2WTypeInput{uuid=a0cbd90a-4e9f-47db-8dca-041d3a288f77, id=0.63 MVA 10/0.4 kV Dyn5 ASEA, rSc=1.7384731670445954 Ω, xSc=9.36379511166658 Ω, sRated=630 kVA, vRatedA=10 kV, vRatedB=0.4 kV, gM=16500 nS, bM=145.8952227629774 nS, dV=2.5 %, dPhi=0.0 °, tapSide=false, tapNeutr=0, tapMin=-2, tapMax=2}, Transformer2WTypeInput{uuid=0843b836-cee4-4a8c-81a4-098400fe91cf, id=0.4 MVA 20/0.4 kV Dyn5 ASEA, rSc=11.999999999999998 Ω, xSc=58.787753826796276 Ω, sRated=400 kVA, vRatedA=20 kV, vRatedB=0.4 kV, gM=2999.9999999999995 nS, bM=24.495101551166183 nS, dV=2.5 %, dPhi=0.0 °, tapSide=false, tapNeutr=0, tapMin=-2, tapMax=2}, Transformer2WTypeInput{uuid=b49db20f-b8b5-4265-8318-f669b9d121e9, id=63 MVA 110/10 kV YNd5, rSc=0.6146031746031745 Ω, xSc=34.56596500037509 Ω, sRated=63000 kVA, vRatedA=110 kV, vRatedB=10 kV, gM=1818.181818181818 nS, bM=1015.6886939330394 nS, dV=1.5 %, dPhi=0.0 °, tapSide=false, tapNeutr=0, tapMin=-9, tapMax=9}),List(Transformer3WTypeInput{uuid=2d4934ac-1d5a-4710-b46b-5bdaba78bcda, id=dummy transformer3W type, sRatedA=null, sRatedB=null, sRatedC=null, vRatedA=110 kV, vRatedB=10 kV, vRatedC=0.4 kV, rScA=null, rScB=null, rScC=null, xScA=null, xScB=null, xScC=null, gM=null, bM=null, dV=null, dPhi=null, tapNeutr=0, tapMin=-2, tapMax=2}, Transformer3WTypeInput{uuid=a3732a04-08f4-44db-b059-16ea04d89363, id=dummy transformer3W type, sRatedA=null, sRatedB=null, sRatedC=null, vRatedA=20 kV, vRatedB=10 kV, vRatedC=0.4 kV, rScA=null, rScB=null, rScC=null, xScA=null, xScB=null, xScC=null, gM=null, bM=null, dV=null, dPhi=null, tapNeutr=0, tapMin=-2, tapMax=2})))' in data awaiting state. Keep on going."
      )
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
          assetInformation
        )
      )

      idleTestKit
        .logEntries()
        .contains(
          CapturedLogEvent(
            Level.DEBUG,
            "Starting conversion for the graph of the grid 1."
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
            nodeInMv2
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
