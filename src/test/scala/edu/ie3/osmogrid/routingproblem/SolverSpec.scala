/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.routingproblem

import edu.ie3.datamodel.graph.DistanceWeightedEdge
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.test.common.{DefinitionsTestData, UnitSpec}

import scala.jdk.CollectionConverters._

class SolverSpec extends UnitSpec with DefinitionsTestData {
  "The Solver" should {
    "calculate the first step correctly" in {
      val firstStep = PrivateMethod[(OsmGraph, List[DistanceWeightedEdge])](
        Symbol("firstStep")
      )

      val (graph, list) =
        Solver invokePrivate firstStep(transitionPoint, connections)

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
  }
}
