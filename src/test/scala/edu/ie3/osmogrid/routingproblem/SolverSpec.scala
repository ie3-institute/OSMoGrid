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
      val osmGraph: OsmGraph = new OsmGraph()
      connections.nodes.foreach { node => osmGraph.addVertex(node) }
      osmGraph.addConnection(
        connections.getConnection(transitionPoint, osmNode1)
      )
      osmGraph.addConnection(
        connections.getConnection(transitionPoint, osmNode2)
      )
      osmGraph.addConnection(connections.getConnection(osmNode1, osmNode3))
      osmGraph.addConnection(connections.getConnection(osmNode3, osmNode2))

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
          nextNode shouldBe osmNode4
          usedConnections shouldBe List(
            connections.getConnection(osmNode2, osmNode4),
            connections.getConnection(osmNode3, osmNode4)
          )
          removedEdge shouldBe osmGraph.getEdge(osmNode3, osmNode2)
          addedWeight.getValue.doubleValue() shouldBe 69297.473649269166029667
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
          nextNode shouldBe osmNode4
          usedConnections shouldBe List(
            connections.getConnection(osmNode1, osmNode4),
            connections.getConnection(osmNode3, osmNode4)
          )
          removedEdge shouldBe osmGraph.getEdge(osmNode1, osmNode3)
          addedWeight.getValue.doubleValue() shouldBe 105675.017994970336106371
        case _ => throw new Error("This should not happen!")
      }
    }
  }
}
