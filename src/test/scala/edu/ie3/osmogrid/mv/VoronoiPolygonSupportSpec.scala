/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */
package edu.ie3.osmogrid.mv

import edu.ie3.datamodel.models.input.NodeInput
import edu.ie3.osmogrid.mv.VoronoiPolygonSupport.VoronoiPolygon
import edu.ie3.test.common.{NodeInputSupport, UnitSpec}
import org.locationtech.jts.geom.Polygon
import org.slf4j.{Logger, LoggerFactory}

class VoronoiPolygonSupportSpec extends UnitSpec with NodeInputSupport {
  "The VoronoiUtils" should {
    val createPolygons =
      PrivateMethod[List[VoronoiPolygon]](Symbol("createPolygons"))
    val polygon: VoronoiPolygon =
      (VoronoiPolygonSupport invokePrivate createPolygons(List(nodeToHv1)))(0)

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
        val polygons: List[Polygon] =
          VoronoiPolygonSupport invokePrivate useBuilder(
            nodes.map(n => n.getGeoPosition.getCoordinate).toList
          )
        polygons.size shouldBe expectedNumber
      }
    }

    "create no voronoi polygons when an empty list is given" in {
      val polygons =
        VoronoiPolygonSupport invokePrivate createPolygons(List.empty)
      polygons.size shouldBe 0
    }

    "create a single voronoi polygon correctly" in {
      val polygons =
        VoronoiPolygonSupport invokePrivate createPolygons(List(nodeToHv1))
      polygons.size shouldBe 1

      val polygon: VoronoiPolygon = polygons(0)
      polygon.transitionPointToHigherVoltLvl shouldBe nodeToHv1
      polygon.transitionPointsToLowerVoltLvl.size shouldBe 0
      polygon.containsNode(nodeToHv1) shouldBe true
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
        val polygons = VoronoiPolygonSupport invokePrivate createPolygons(nodes)
        polygons.size shouldBe expectedSize
      }
    }

    "create a voronoi diagram correctly" in {
      val hvToMvNodes = List(nodeToHv1, nodeToHv2, nodeToHv3, nodeToHv4)

      val polygons: List[VoronoiPolygon] =
        VoronoiPolygonSupport invokePrivate createPolygons(hvToMvNodes)

      polygons.size shouldBe 4

      // making sure that each voronoi polygon has its own unique polygon
      polygons(0).polygon should not be polygons(1).polygon
      polygons(1).polygon should not be polygons(2).polygon
      polygons(2).polygon should not be polygons(3).polygon
      polygons(3).polygon should not be polygons(0).polygon

      polygons(0).transitionPointToHigherVoltLvl shouldBe nodeToHv1
      polygons(0).transitionPointsToLowerVoltLvl.size shouldBe 0
      polygons(0).containsNode(nodeToHv1) shouldBe true
      polygons(0).containsNode(nodeToHv2) shouldBe false
      polygons(0).containsNode(nodeToHv3) shouldBe false
      polygons(0).containsNode(nodeToHv4) shouldBe false

      polygons(1).transitionPointToHigherVoltLvl shouldBe nodeToHv2
      polygons(1).transitionPointsToLowerVoltLvl.size shouldBe 0
      polygons(1).containsNode(nodeToHv2) shouldBe true
      polygons(1).containsNode(nodeToHv1) shouldBe false
      polygons(1).containsNode(nodeToHv3) shouldBe false
      polygons(1).containsNode(nodeToHv4) shouldBe false

      polygons(2).transitionPointToHigherVoltLvl shouldBe nodeToHv3
      polygons(2).transitionPointsToLowerVoltLvl.size shouldBe 0
      polygons(2).containsNode(nodeToHv3) shouldBe true
      polygons(2).containsNode(nodeToHv1) shouldBe false
      polygons(2).containsNode(nodeToHv2) shouldBe false
      polygons(2).containsNode(nodeToHv4) shouldBe false

      polygons(3).transitionPointToHigherVoltLvl shouldBe nodeToHv4
      polygons(3).transitionPointsToLowerVoltLvl.size shouldBe 0
      polygons(3).containsNode(nodeToHv4) shouldBe true
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

    "update a voronoi polygon correctly" in {
      val updatePolygons =
        PrivateMethod[(List[VoronoiPolygon], List[NodeInput])](
          Symbol("updatePolygons")
        )
      val log: Logger = LoggerFactory.getLogger(VoronoiPolygonSupport.getClass)

      val polygons: List[VoronoiPolygon] = List(polygon)
      val nodes = List(nodeInMv1, nodeInMv2, nodeInMv3)
      val (updatedPolygon, notAssigned) =
        VoronoiPolygonSupport invokePrivate updatePolygons(polygons, nodes, log)

      notAssigned shouldBe List()
      updatedPolygon(0).transitionPointsToLowerVoltLvl shouldBe List(
        nodeInMv1,
        nodeInMv2,
        nodeInMv3
      )
    }

    "update a voronoi polygons correctly" in {
      val updatePolygons =
        PrivateMethod[(List[VoronoiPolygon], List[NodeInput])](
          Symbol("updatePolygons")
        )
      val log: Logger = LoggerFactory.getLogger(VoronoiPolygonSupport.getClass)

      val polygons: List[VoronoiPolygon] =
        VoronoiPolygonSupport invokePrivate createPolygons(
          List(nodeToHv1, nodeToHv2, nodeToHv3, nodeToHv4)
        )

      val cases = Table(
        ("nodes", "expectedNotAssigned", "l1", "l2", "l3", "l4"),
        (List(nodeOutside), List(nodeOutside), List(), List(), List(), List()),
        (
          List(nodeInMv1, nodeOutside),
          List(nodeOutside),
          List(nodeInMv1),
          List(),
          List(),
          List()
        ),
        (
          List(nodeInMv1, nodeInMv2),
          List(),
          List(nodeInMv1),
          List(nodeInMv2),
          List(),
          List()
        ),
        (
          List(nodeInMv1, nodeInMv3, nodeOutside),
          List(nodeOutside),
          List(nodeInMv1),
          List(),
          List(nodeInMv3),
          List()
        ),
        (
          List(nodeInMv1, nodeInMv2, nodeInMv4, nodeOutside),
          List(nodeOutside),
          List(nodeInMv1),
          List(nodeInMv2),
          List(),
          List(nodeInMv4)
        )
      )

      forAll(cases) { (nodes, expectedNotAssigned, l1, l2, l3, l4) =>
        val (updatedPolygon, notAssigned) =
          VoronoiPolygonSupport invokePrivate updatePolygons(
            polygons,
            nodes,
            log
          )

        notAssigned shouldBe expectedNotAssigned
        updatedPolygon(0).transitionPointsToLowerVoltLvl shouldBe l1
        updatedPolygon(1).transitionPointsToLowerVoltLvl shouldBe l2
        updatedPolygon(2).transitionPointsToLowerVoltLvl shouldBe l3
        updatedPolygon(3).transitionPointsToLowerVoltLvl shouldBe l4
      }
    }
  }
}
