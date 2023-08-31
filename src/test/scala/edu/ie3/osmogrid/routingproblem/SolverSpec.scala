/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.routingproblem

import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.osmogrid.routingproblem.Definitions.{
  StepResult,
  StepResultOption
}
import edu.ie3.test.common.{DefinitionsTestData, UnitSpec}
import tech.units.indriya.quantity.Quantities
import tech.units.indriya.unit.Units

import scala.jdk.CollectionConverters._

class SolverSpec extends UnitSpec with DefinitionsTestData {
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

      val expectedRemovedEdge = graph.getEdge(osmNode1, osmNode2)

      val cases = Table(
        ("neighbor", "connectionA", "connectionB", "weight"),
        (
          osmNode3,
          connections.getConnection(osmNode1, osmNode3),
          connections.getConnection(osmNode2, osmNode3),
          196992.47141155004
        ),
        (
          osmNode4,
          connections.getConnection(osmNode1, osmNode4),
          connections.getConnection(osmNode2, osmNode4),
          233370.01575725118
        ),
        (
          osmNode5,
          connections.getConnection(osmNode1, osmNode5),
          connections.getConnection(osmNode2, osmNode5),
          193026.02665479417
        ),
        (
          osmNode6,
          connections.getConnection(osmNode1, osmNode6),
          connections.getConnection(osmNode2, osmNode6),
          81917.37382966773
        )
      )

      forAll(cases) { (neighbor, connectionA, connectionB, weight) =>
        {
          val list = Solver invokePrivate calcStepResultOptions(
            transitionPoint,
            node,
            neighbor,
            graph,
            edges,
            connections
          )

          list.size shouldBe 1

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
              removedEdge shouldBe expectedRemovedEdge
              addedWeight.getValue.doubleValue() shouldBe weight
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

    "check step result options correctly" in {
      val checkStepResultOptions =
        PrivateMethod[List[StepResultOption]](Symbol("checkStepResultOptions"))

      val res1 = StepResultOption(
        new OsmGraph(),
        osmNode1,
        List.empty,
        null,
        Quantities.getQuantity(3, Units.METRE)
      )
      val res2 = StepResultOption(
        new OsmGraph(),
        osmNode1,
        List.empty,
        null,
        Quantities.getQuantity(6, Units.METRE)
      )
      val res3 = StepResultOption(
        new OsmGraph(),
        osmNode1,
        List.empty,
        null,
        Quantities.getQuantity(1, Units.METRE)
      )

      val cases = Table(
        ("stepResultOptions", "checked"),
        (List.empty, List.empty),
        (
          List(stepResultOptionsForThirdStep(0)),
          List(stepResultOptionsForThirdStep(0))
        ),
        (List(stepResultOptionsForThirdStep(1)), List.empty),
        (stepResultOptionsForThirdStep, List(stepResultOptionsForThirdStep(0))),
        (List(res1, res2, res3), List(res3, res1, res2))
      )

      forAll(cases) { (stepResultOptions, checked) =>
        val result =
          Solver invokePrivate checkStepResultOptions(stepResultOptions)
        result shouldBe checked
      }
    }
  }
}
