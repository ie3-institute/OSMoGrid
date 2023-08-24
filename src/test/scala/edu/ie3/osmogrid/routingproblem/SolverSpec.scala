/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.routingproblem

import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.test.common.{DefinitionsTestData, UnitSpec}
import edu.ie3.util.osm.model.OsmEntity.Node

class SolverSpec extends UnitSpec with DefinitionsTestData {
  "The Solver" should {
    "calculate the first step correctly" in {
      val firstStep = PrivateMethod[(OsmGraph, List[Node])](Symbol("firstStep"))

      val (graph, list) =
        Solver invokePrivate firstStep(transitionPoint, connections)

      val vertexSet = graph.vertexSet()
      val edgeSet = graph.edgeSet()

      list shouldBe List(osmNode1, osmNode2)
      vertexSet.size shouldBe 7
      edgeSet.size shouldBe 7

    }

  }
}
