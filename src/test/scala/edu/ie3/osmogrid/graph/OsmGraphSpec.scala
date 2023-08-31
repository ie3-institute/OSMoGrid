/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.graph

import edu.ie3.osmogrid.routingproblem.Definitions.Connection
import edu.ie3.test.common.{DefinitionsTestData, UnitSpec}
import tech.units.indriya.quantity.Quantities
import tech.units.indriya.unit.Units

import scala.jdk.CollectionConverters._

class OsmGraphSpec extends UnitSpec with DefinitionsTestData {
  "The OsmGraph" should {
    // returns a new graph
    def newGraph: OsmGraph = {
      val graph = new OsmGraph()
      List(transitionPoint, osmNode1, osmNode2).foreach { vertex =>
        graph.addVertex(vertex)
      }
      graph.addWeightedEdge(transitionPoint, osmNode1)
      graph.addWeightedEdge(transitionPoint, osmNode2)

      graph
    }

    "be copied correctly" in {
      val graph = newGraph

      val copiedGraph = graph.copy()
      graph.removeVertex(transitionPoint)

      copiedGraph.vertexSet().asScala shouldBe Set(
        transitionPoint,
        osmNode1,
        osmNode2
      )
      copiedGraph.edgeSet().asScala shouldBe Set(
        copiedGraph.getEdge(transitionPoint, osmNode1),
        copiedGraph.getEdge(transitionPoint, osmNode2)
      )

      graph.vertexSet().asScala shouldBe Set(osmNode1, osmNode2)
      graph.edgeSet().asScala shouldBe Set.empty
    }

    "reconnect nodes correctly" in {
      val connection = Connection(
        osmNode1,
        osmNode2,
        Quantities.getQuantity(1d, Units.METRE),
        None
      )

      val cases = Table(
        ("doubleEdges", "expectedDoubleEdges", "expectedEdgeSet"),
        (List.empty, List.empty, Set((osmNode1, osmNode2))),
        (
          List((transitionPoint, osmNode1)),
          List.empty,
          Set(
            (transitionPoint, osmNode1),
            (osmNode1, osmNode2)
          )
        ),
        (
          List((transitionPoint, osmNode1), (transitionPoint, osmNode2)),
          List.empty,
          Set(
            (transitionPoint, osmNode1),
            (transitionPoint, osmNode2),
            (osmNode1, osmNode2)
          )
        )
      )

      forAll(cases) { (doubleEdges, expectedDoubleEdges, expectedEdgeSet) =>
        val graph = newGraph
        val actualDoubleEdges = doubleEdges.map { case (nodeA, nodeB) =>
          graph.getEdge(nodeA, nodeB)
        }

        val updatedDoubleEdges =
          graph.reconnectNodes(transitionPoint, connection, actualDoubleEdges)
        updatedDoubleEdges shouldBe expectedDoubleEdges

        val edgeSet = expectedEdgeSet.map { case (nodeA, nodeB) =>
          graph.getEdge(nodeA, nodeB)
        }
        graph.edgeSet().asScala shouldBe edgeSet

        graph.vertexSet().asScala shouldBe Set(
          transitionPoint,
          osmNode1,
          osmNode2
        )
      }
    }

    "check if edges intersects each other correctly" in {
      val graph = new OsmGraph()
      connections.nodes.foreach { node => graph.addVertex(node) }
      graph.addEdge(osmNode1, osmNode4)
      graph.addEdge(transitionPoint, osmNode5)

      val cases = Table(
        ("source", "target", "result"),
        (transitionPoint, osmNode1, false),
        (osmNode1, osmNode2, true),
        (osmNode3, osmNode4, false),
        (osmNode3, osmNode6, true),
        (osmNode2, osmNode3, true),
        (osmNode4, osmNode5, false)
      )

      forAll(cases) { (source, target, result) =>
        val copy = graph.copy()
        copy.addEdge(source, target)

        copy.containsEdgeIntersection() shouldBe result
      }
    }
  }
}
