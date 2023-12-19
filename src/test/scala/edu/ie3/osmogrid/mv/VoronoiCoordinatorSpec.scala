/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.mv

import org.apache.pekko.actor.testkit.typed.CapturedLogEvent
import org.apache.pekko.actor.testkit.typed.scaladsl.{
  ActorTestKit,
  BehaviorTestKit,
  ScalaTestWithActorTestKit
}
import edu.ie3.osmogrid.cfg.OsmoGridConfigFactory
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.test.common.{MvTestData, UnitSpec}
import edu.ie3.test.common.{GridSupport, MvTestData, UnitSpec}
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
          nodeToHv,
          new NodeConversion(Map.empty, Map.empty),
          assetInformation
        )
      )

      idleTestKit.logEntries() should contain only CapturedLogEvent(
        Level.WARN,
        "Received unsupported message 'StartMvGraphConversion(1,([], []),NodeInput{uuid=92c3a19d-2a07-4472-bd7a-cbb49a5ae5fd, id='Transition point', operator=8d4b3c30-8622-496f-831b-9376e367c499, operationTime=OperationTime{startDate=null, endDate=null, isLimited=false}, vTarget=1 p.u., slack=true, geoPosition=POINT (7 50), voltLvl=CommonVoltageLevel{id='Mittelspannung', nominalVoltage=10 kV, synonymousIds=[Mittelspannung, ms, ms_10kv, mv, mv_10kV], voltageRange=Interval [10 kV, 20 kV)}, subnet=1},NodeConversion(Map(),Map()),AssetInformation(List(LineTypeInput{uuid=6b223bc3-69e2-4eb8-a2c0-76be1cd2c998, id=NA2XS2Y 1x400 RM/25 6/10 kV, b=1.6964599999999997E8 µS/km, g=0.0 µS/km, r=0.078 Ω/km, x=0.0942 Ω/km, iMax=535 A, vRated=10 kV}),List(Transformer2WTypeInput{uuid=a0cbd90a-4e9f-47db-8dca-041d3a288f77, id=0.63 MVA 10/0.4 kV Dyn5 ASEA, rSc=1.7384731670445954 Ω, xSc=9.36379511166658 Ω, sRated=630 kVA, vRatedA=10 kV, vRatedB=0.4 kV, gM=16500 nS, bM=145.8952227629774 nS, dV=2.5 %, dPhi=0.0 °, tapSide=false, tapNeutr=0, tapMin=-2, tapMax=2}, Transformer2WTypeInput{uuid=0843b836-cee4-4a8c-81a4-098400fe91cf, id=0.4 MVA 20/0.4 kV Dyn5 ASEA, rSc=11.999999999999998 Ω, xSc=58.787753826796276 Ω, sRated=400 kVA, vRatedA=20 kV, vRatedB=0.4 kV, gM=2999.9999999999995 nS, bM=24.495101551166183 nS, dV=2.5 %, dPhi=0.0 °, tapSide=false, tapNeutr=0, tapMin=-2, tapMax=2})))' in data awaiting state. Keep on going."
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
        StartMvGraphGeneration(1, polygon, streetGraph, assetInformation)
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
          nodeToHv,
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
      val message = mvCoordinator.receiveMessage

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
          nodeChanges should contain allElementsOf Seq(
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
