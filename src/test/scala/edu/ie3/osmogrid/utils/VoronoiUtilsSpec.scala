/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.utils

import edu.ie3.test.common.{NodeInputSupport, UnitSpec}
import utils.VoronoiUtils.{
  VoronoiPolygon,
  createPolygons,
  createVoronoiPolygons
}

class VoronoiUtilsSpec extends UnitSpec with NodeInputSupport {
  "The VoronoiUtils" should {
    "create no voronoi polygons when an empty list is given" in {
      val polygons = createPolygons(List.empty)

      polygons.size shouldBe 0
    }

    "create a single voronoi polygon correctly" in {
      val polygons = createPolygons(List(nodeToHv1))

      polygons.size shouldBe 1

      val polygon: VoronoiPolygon = polygons(0)
      polygon.transitionPointToHigherVoltLvl shouldBe nodeToHv1
      polygon.transitionPointsToLowerVoltLvl.size shouldBe 0
      polygon.containsNode(nodeToHv1)
      polygon.containsNode(nodeToHv2) shouldBe false
      polygon.containsNode(nodeToHv3) shouldBe false
    }

    "create a voronoi diagram correctly" in {
      val hvToMvNodes = List(nodeToHv1, nodeToHv2, nodeToHv3)

      val polygons: List[VoronoiPolygon] = createPolygons(hvToMvNodes)

      polygons.size shouldBe 3

      polygons(0).transitionPointToHigherVoltLvl shouldBe nodeToHv1
      polygons(0).transitionPointsToLowerVoltLvl.size shouldBe 0
      polygons(0).containsNode(nodeToHv1)
      polygons(0).containsNode(nodeToHv2) shouldBe false
      polygons(0).containsNode(nodeToHv3) shouldBe false

      polygons(1).transitionPointToHigherVoltLvl shouldBe nodeToHv2
      polygons(1).transitionPointsToLowerVoltLvl.size shouldBe 0
      polygons(1).containsNode(nodeToHv2)
      polygons(1).containsNode(nodeToHv1) shouldBe false
      polygons(1).containsNode(nodeToHv3) shouldBe false

      polygons(2).transitionPointToHigherVoltLvl shouldBe nodeToHv3
      polygons(2).transitionPointsToLowerVoltLvl.size shouldBe 0
      polygons(2).containsNode(nodeToHv3)
      polygons(2).containsNode(nodeToHv1) shouldBe false
      polygons(2).containsNode(nodeToHv2) shouldBe false
    }

    "update a voronoi polygon correctly" in {}

  }
}
