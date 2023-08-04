/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.utils

import edu.ie3.test.common.{NodeInputSupport, UnitSpec}
import org.locationtech.jts.geom.Polygon
import utils.VoronoiUtils
import utils.VoronoiUtils.VoronoiPolygon

class VoronoiUtilsSpec extends UnitSpec with NodeInputSupport {
  "The VoronoiUtils" should {
    val createPolygons =
      PrivateMethod[List[VoronoiPolygon]](Symbol("createPolygons"))
    val polygon: VoronoiPolygon =
      (VoronoiUtils invokePrivate createPolygons(List(nodeToHv1)))(0)

    "generate polygons correctly" in {
      val useBuilder = PrivateMethod[List[Polygon]](Symbol("useBuilder"))

      val cases = Table(
        ("nodes", "expectedNumber"),
        (Seq(nodeToHv1), 0),
        (Seq(nodeToHv1, nodeToHv2), 2),
        (Seq(nodeToHv1, nodeToHv2, nodeToHv3), 3),
        (Seq(nodeToHv1, nodeToHv2, nodeToHv3, nodeToHv4), 4),
        (Seq(nodeToHv1, nodeInMv1), 2),
        (Seq(nodeToHv3, nodeInMv2), 2)
      )

      forAll(cases) { (nodes, expectedNumber) =>
        val polygons: List[Polygon] = VoronoiUtils invokePrivate useBuilder(
          nodes.map(n => n.getGeoPosition.getCoordinate).toList
        )
        polygons.size shouldBe expectedNumber
      }
    }

    "create no voronoi polygons when an empty list is given" in {
      val polygons = VoronoiUtils invokePrivate createPolygons(List.empty)
      polygons.size shouldBe 0
    }

    "create a single voronoi polygon correctly" in {
      val polygons = VoronoiUtils invokePrivate createPolygons(List(nodeToHv1))
      polygons.size shouldBe 1

      val polygon: VoronoiPolygon = polygons(0)
      polygon.transitionPointToHigherVoltLvl shouldBe nodeToHv1
      polygon.transitionPointsToLowerVoltLvl.size shouldBe 0
      polygon.containsNode(nodeToHv1)
      polygon.containsNode(nodeToHv2) shouldBe true
      polygon.containsNode(nodeToHv3) shouldBe true
    }

    "create the correct amount of voronoi polygons" in {
      val cases = Table(
        ("nodes", "expectedSize"),
        (List(nodeToHv1), 1),
        (List(nodeToHv1, nodeToHv2), 2),
        (List(nodeToHv1, nodeToHv2, nodeToHv3), 3),
        (List(nodeToHv1, nodeToHv2, nodeToHv3, nodeToHv4), 4)
      )

      forAll(cases) { (nodes, expectedSize) =>
        val polygons = VoronoiUtils invokePrivate createPolygons(nodes)
        polygons.size shouldBe expectedSize
      }
    }

    "create a voronoi diagram correctly" in {
      val hvToMvNodes = List(nodeToHv1, nodeToHv2, nodeToHv3, nodeToHv4)

      val polygons: List[VoronoiPolygon] =
        VoronoiUtils invokePrivate createPolygons(hvToMvNodes)

      polygons.size shouldBe 4

      // making sure that each voronoi polygon has its own unique polygon
      polygons(0).polygon should not be polygons(1).polygon
      polygons(1).polygon should not be polygons(2).polygon
      polygons(2).polygon should not be polygons(3).polygon
      polygons(3).polygon should not be polygons(0).polygon

      polygons(0).transitionPointToHigherVoltLvl shouldBe nodeToHv1
      polygons(0).transitionPointsToLowerVoltLvl.size shouldBe 0
      polygons(0).containsNode(nodeToHv1)
      polygons(0).containsNode(nodeToHv2) shouldBe false
      polygons(0).containsNode(nodeToHv3) shouldBe false
      polygons(0).containsNode(nodeToHv4) shouldBe false

      polygons(1).transitionPointToHigherVoltLvl shouldBe nodeToHv2
      polygons(1).transitionPointsToLowerVoltLvl.size shouldBe 0
      polygons(1).containsNode(nodeToHv2)
      polygons(1).containsNode(nodeToHv1) shouldBe false
      polygons(1).containsNode(nodeToHv3) shouldBe false
      polygons(1).containsNode(nodeToHv4) shouldBe false

      polygons(2).transitionPointToHigherVoltLvl shouldBe nodeToHv3
      polygons(2).transitionPointsToLowerVoltLvl.size shouldBe 0
      polygons(2).containsNode(nodeToHv3)
      polygons(2).containsNode(nodeToHv1) shouldBe false
      polygons(2).containsNode(nodeToHv2) shouldBe false
      polygons(2).containsNode(nodeToHv4) shouldBe false

      polygons(3).transitionPointToHigherVoltLvl shouldBe nodeToHv4
      polygons(3).transitionPointsToLowerVoltLvl.size shouldBe 0
      polygons(3).containsNode(nodeToHv4)
      polygons(3).containsNode(nodeToHv1) shouldBe false
      polygons(3).containsNode(nodeToHv2) shouldBe false
      polygons(3).containsNode(nodeToHv3) shouldBe false
    }

    "check if a node is inside a voronoi polygon" in {
      polygon.containsNode(nodeInMv1) shouldBe true
      polygon.containsNode(nodeInMv2) shouldBe true
      polygon.containsNode(nodeInMv3) shouldBe true
      polygon.containsNode(nodeInMv4) shouldBe true
    }

    "update a voronoi polygon correctly" in {}

  }
}
