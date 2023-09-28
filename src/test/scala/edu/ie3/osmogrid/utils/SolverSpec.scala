/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.utils

import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.test.common.{MvTestData, UnitSpec}
import tech.units.indriya.quantity.Quantities
import tech.units.indriya.unit.Units
import utils.Solver
import utils.Solver.{StepResult, StepResultOption}

import scala.jdk.CollectionConverters._

class SolverSpec extends UnitSpec with MvTestData {
  "The Solver" should {
    val firstStep = PrivateMethod[StepResult](
      Symbol("firstStep")
    )

    val calcStepResultOptions =
      PrivateMethod[List[StepResultOption]](Symbol("calcStepResultOptions"))

    "calculate the first step correctly" in {
      val stepResult =
        Solver invokePrivate firstStep(transitionPoint, connections)

      stepResult.nextNode shouldBe osmNode1

      val graph = stepResult.graph

      // the graph should contain all given vertexes
      graph.vertexSet().asScala shouldBe Set(
        transitionPoint,
        osmNode1,
        osmNode2,
        osmNode3,
        osmNode4,
        osmNode5,
        osmNode6
      )

      // the graph should contain all edges
      graph.edgeSet().asScala shouldBe Set(
        graph.getEdge(transitionPoint, osmNode1),
        graph.getEdge(transitionPoint, osmNode2),
        graph.getEdge(osmNode1, osmNode2)
      )

      // there should be some double edges
      stepResult.notConnectedNodes shouldBe List(
        osmNode3,
        osmNode4,
        osmNode5,
        osmNode6
      )
    }

    "calculate step result options for wrong neighbors correctly" in {
      val stepResult =
        Solver invokePrivate firstStep(transitionPoint, connections)
      val (graph, node) = (stepResult.graph, stepResult.nextNode)
      val edges = graph.getSortedEdges(node)

      val cases = Table(
        ("neighbor", "options"),
        (transitionPoint, List()),
        (osmNode2, List())
      )

      forAll(cases) { (neighbor, options) =>
        {
          Solver invokePrivate calcStepResultOptions(
            transitionPoint,
            node,
            neighbor,
            graph,
            edges,
            connections
          ) shouldBe options
        }
      }
    }

    "calculate step result options for second step correctly" in {
      val stepResult =
        Solver invokePrivate firstStep(transitionPoint, connections)
      val (graph, node) = (stepResult.graph, stepResult.nextNode)
      val edges = graph.getSortedEdges(node)

      val expectedRemovedEdgeA = graph.getEdge(osmNode1, osmNode2)
      val expectedRemovedEdgeB = graph.getEdge(transitionPoint, osmNode1)

      val cases = Table(
        (
          "neighbor",
          "connectionA",
          "connectionB",
          "connectionC",
          "weightA",
          "weightB"
        ),
        (
          osmNode3,
          connections.getConnection(osmNode1, osmNode3),
          connections.getConnection(osmNode2, osmNode3),
          connections.getConnection(transitionPoint, osmNode3),
          196992.47141155004,
          231691.124434181291374125
        ),
        (
          osmNode4,
          connections.getConnection(osmNode1, osmNode4),
          connections.getConnection(osmNode2, osmNode4),
          connections.getConnection(transitionPoint, osmNode4),
          233370.01575725118,
          295002.37070387924864776
        ),
        (
          osmNode5,
          connections.getConnection(osmNode1, osmNode5),
          connections.getConnection(osmNode2, osmNode5),
          connections.getConnection(transitionPoint, osmNode5),
          193026.02665479417,
          275916.254133759888548824
        ),
        (
          osmNode6,
          connections.getConnection(osmNode1, osmNode6),
          connections.getConnection(osmNode2, osmNode6),
          connections.getConnection(transitionPoint, osmNode6),
          81917.37382966773,
          170956.771658949236933978
        )
      )

      forAll(cases) {
        (neighbor, connectionA, connectionB, connectionC, weightA, weightB) =>
          {
            val list = Solver invokePrivate calcStepResultOptions(
              transitionPoint,
              node,
              neighbor,
              graph,
              edges,
              connections
            )

            list.size shouldBe 2

            list(0) match {
              case StepResultOption(
                    graph,
                    nextNode,
                    usedConnections,
                    removedEdge,
                    addedWeight
                  ) =>
                graph.edgeSet().size() shouldBe 4
                nextNode shouldBe neighbor
                usedConnections shouldBe List(connectionA, connectionB)
                removedEdge shouldBe expectedRemovedEdgeA
                addedWeight.getValue.doubleValue() shouldBe weightA
              case _ => throw new Error("This should not happen!")
            }

            list(1) match {
              case StepResultOption(
                    graph,
                    nextNode,
                    usedConnections,
                    removedEdge,
                    addedWeight
                  ) =>
                graph.edgeSet().size() shouldBe 4
                nextNode shouldBe neighbor
                usedConnections shouldBe List(connectionC, connectionA)
                removedEdge shouldBe expectedRemovedEdgeB
                addedWeight.getValue.doubleValue() shouldBe weightB
              case _ => throw new Error("This should not happen!")
            }
          }
      }
    }

    "calculate step result options for any step correctly" in {
      // ring: tp -> 1 -> 3 -> 2 -> tp
      val osmGraph: OsmGraph = graphAfterTwoSteps

      val stepResultOptions: List[StepResultOption] =
        Solver invokePrivate calcStepResultOptions(
          transitionPoint,
          osmNode3,
          osmNode4,
          osmGraph,
          osmGraph.getSortedEdges(osmNode3),
          connections
        )

      stepResultOptions.size shouldBe 2

      stepResultOptions(0) match {
        case StepResultOption(
              graph,
              nextNode,
              usedConnections,
              removedEdge,
              addedWeight
            ) =>
          graph.edgeSet().size() shouldBe 5
          nextNode shouldBe stepResultOptionsForThirdStep(0).nextNode
          usedConnections shouldBe stepResultOptionsForThirdStep(
            0
          ).usedConnections
          removedEdge shouldBe stepResultOptionsForThirdStep(0).removedEdge
          addedWeight.isEquivalentTo(
            stepResultOptionsForThirdStep(0).addedWeight
          ) shouldBe true
        case _ => throw new Error("This should not happen!")
      }

      stepResultOptions(1) match {
        case StepResultOption(
              graph,
              nextNode,
              usedConnections,
              removedEdge,
              addedWeight
            ) =>
          graph.edgeSet().size() shouldBe 5
          nextNode shouldBe stepResultOptionsForThirdStep(1).nextNode
          usedConnections shouldBe stepResultOptionsForThirdStep(
            1
          ).usedConnections
          removedEdge shouldBe stepResultOptionsForThirdStep(1).removedEdge
          addedWeight.isEquivalentTo(
            stepResultOptionsForThirdStep(1).addedWeight
          ) shouldBe true
        case _ => throw new Error("This should not happen!")
      }
    }

    "evaluate step result options correctly" in {
      val evaluateStepResultOptions =
        PrivateMethod[Option[StepResult]](Symbol("evaluateStepResultOptions"))
      val notConnectedNodes =
        List(osmNode1, osmNode2, osmNode3, osmNode5, osmNode6)

      val res1 = StepResultOption(
        new OsmGraph(),
        osmNode1,
        List.empty,
        null,
        Quantities.getQuantity(3, Units.METRE)
      )
      val res2 = StepResultOption(
        new OsmGraph(),
        osmNode2,
        List.empty,
        null,
        Quantities.getQuantity(6, Units.METRE)
      )
      val res3 = StepResultOption(
        new OsmGraph(),
        osmNode3,
        List.empty,
        null,
        Quantities.getQuantity(1, Units.METRE)
      )

      val cases = Table(
        ("options", "result"),
        (
          List(res1),
          Some(
            StepResult(
              new OsmGraph(),
              osmNode1,
              List(osmNode2, osmNode3, osmNode5, osmNode6)
            )
          )
        ),
        (
          List(res2),
          Some(
            StepResult(
              new OsmGraph(),
              osmNode2,
              List(osmNode1, osmNode3, osmNode5, osmNode6)
            )
          )
        ),
        (
          List(res1, res2),
          Some(
            StepResult(
              new OsmGraph(),
              osmNode1,
              List(osmNode2, osmNode3, osmNode5, osmNode6)
            )
          )
        ),
        (
          List(res1, res3),
          Some(
            StepResult(
              new OsmGraph(),
              osmNode3,
              List(osmNode1, osmNode2, osmNode5, osmNode6)
            )
          )
        ),
        (
          List(res1, res2, res3),
          Some(
            StepResult(
              new OsmGraph(),
              osmNode3,
              List(osmNode1, osmNode2, osmNode5, osmNode6)
            )
          )
        ),
        (List(), None)
      )

      forAll(cases) { (options, result) =>
        val option: Option[StepResult] =
          Solver invokePrivate evaluateStepResultOptions(
            options,
            notConnectedNodes
          )

        option shouldBe result
      }
    }

    "work correctly" in {
      val graph = Solver.solve(transitionPoint, connections)
      val vertexes = graph.vertexSet().asScala.toList
      val edges = graph.edgeSet().asScala

      vertexes.size shouldBe 7
      edges.size shouldBe 7

      vertexes shouldBe connections.nodes
      edges.contains(graph.getEdge(transitionPoint, osmNode1)) shouldBe true
      edges.contains(graph.getEdge(transitionPoint, osmNode2)) shouldBe true
      edges.contains(graph.getEdge(osmNode1, osmNode3)) shouldBe true
      edges.contains(graph.getEdge(osmNode2, osmNode6)) shouldBe true
      edges.contains(graph.getEdge(osmNode3, osmNode4)) shouldBe true
      edges.contains(graph.getEdge(osmNode4, osmNode5)) shouldBe true
      edges.contains(graph.getEdge(osmNode5, osmNode6)) shouldBe true
    }
  }
}
