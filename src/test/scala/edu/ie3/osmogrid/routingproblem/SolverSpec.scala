/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.routingproblem

import edu.ie3.datamodel.graph.DistanceWeightedEdge
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.osmogrid.routingproblem.Definitions.Saving
import edu.ie3.test.common.{DefinitionsTestData, UnitSpec}
import edu.ie3.util.osm.model.OsmEntity.Node
import tech.units.indriya.quantity.Quantities
import tech.units.indriya.unit.Units

import scala.jdk.CollectionConverters._

class SolverSpec extends UnitSpec with DefinitionsTestData {
  "The Solver" should {
    val firstStep = PrivateMethod[(OsmGraph, Node, List[DistanceWeightedEdge])](
      Symbol("firstStep")
    )

    val salcSavings = PrivateMethod[List[Saving]](Symbol("calcSavings"))

    "calculate the first step correctly" in {
      val (graph, node, list) =
        Solver invokePrivate firstStep(transitionPoint, connections)

      node shouldBe osmNode1

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
        graph.getEdge(transitionPoint, osmNode3),
        graph.getEdge(transitionPoint, osmNode4),
        graph.getEdge(transitionPoint, osmNode5),
        graph.getEdge(transitionPoint, osmNode6),
        graph.getEdge(transitionPoint, osmNode1),
        graph.getEdge(transitionPoint, osmNode2),
        graph.getEdge(osmNode1, osmNode2)
      )

      // there should be some double edges
      list shouldBe List(
        graph.getEdge(transitionPoint, osmNode3),
        graph.getEdge(transitionPoint, osmNode4),
        graph.getEdge(transitionPoint, osmNode5),
        graph.getEdge(transitionPoint, osmNode6)
      )
    }

    "calculate savings correctly" in {
      val (graph, node, list) =
        Solver invokePrivate firstStep(transitionPoint, connections)
      val edges = graph.getSortedEdges(node)
      val savings = Solver invokePrivate salcSavings(
        transitionPoint,
        node,
        osmNode3,
        graph,
        edges,
        list,
        connections
      )

      savings.size shouldBe 1
      savings(0) match {
        case Saving(
              usedConnectionA,
              usedConnectionB,
              removedEdge,
              removedDoubleEdge,
              updatedGraph,
              saving
            ) =>
          usedConnectionA shouldBe connections.getConnection(osmNode1, osmNode3)
          usedConnectionB shouldBe connections.getConnection(osmNode2, osmNode3)
          removedEdge shouldBe graph.getEdge(osmNode1, osmNode2)
          removedDoubleEdge shouldBe Some(
            graph.getEdge(transitionPoint, osmNode3)
          )
          updatedGraph.edgeSet().size() shouldBe 7
          saving.getValue.doubleValue() shouldBe 53951.655141201058625875
        case _ => throw new Error("This should not happen!")
      }

    }

  }
}
