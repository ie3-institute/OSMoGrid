/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.utils

import edu.ie3.datamodel.models.voltagelevels.GermanVoltageLevelUtils
import edu.ie3.test.common.{MvTestData, OsmTestData, UnitSpec}
import edu.ie3.util.geo.GeoUtils.buildCoordinate
import utils.OsmoGridUtils.{buildStreetGraph, spawnDummyHvNode}

import scala.jdk.CollectionConverters._

class OsmoGridUtilsSpec extends UnitSpec with MvTestData with OsmTestData {
  "The OsmoGridUtils" should {
    "build a street graph correctly" in {
      val (ways, nodes) = data
      val streetGraph = buildStreetGraph(ways, nodes)

      val vertexes = streetGraph.vertexSet().asScala
      val edges = streetGraph.edgeSet().asScala
      vertexes.size shouldBe 6
      edges.size shouldBe 6

      vertexes shouldBe Set(node1, node2, node3, node4, node5, node6)
      edges shouldBe Set(
        streetGraph.getEdge(node1, node2),
        streetGraph.getEdge(node2, node3),
        streetGraph.getEdge(node3, node4),
        streetGraph.getEdge(node4, node5),
        streetGraph.getEdge(node5, node6),
        streetGraph.getEdge(node6, node1)
      )
    }

    "spawn a dummy hv node correctly" in {
      val hvNode1 = spawnDummyHvNode(
        List(nodeInMv1, nodeInMv2, nodeInMv3, nodeInMv4, nodeInMv5, nodeInMv6)
      )

      hvNode1.getId shouldBe "Hv node of Node 6"
      hvNode1.getvTarget().getValue.doubleValue() shouldBe 1d
      hvNode1.isSlack shouldBe true
      hvNode1.getGeoPosition.getCoordinate shouldBe buildCoordinate(50.5, 8.5)
      hvNode1.getVoltLvl shouldBe GermanVoltageLevelUtils.HV
      hvNode1.getSubnet shouldBe 1001

      val hvNode2 = spawnDummyHvNode(List(nodeInMv1))

      hvNode2.getId shouldBe "Hv node of Node 1"
      hvNode2.getvTarget().getValue.doubleValue() shouldBe 1d
      hvNode2.isSlack shouldBe true
      hvNode2.getGeoPosition.getCoordinate shouldBe buildCoordinate(50.5, 7)
      hvNode2.getVoltLvl shouldBe GermanVoltageLevelUtils.HV
      hvNode2.getSubnet shouldBe 1001

    }
  }
}
