/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv.coordinator

import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.osmogrid.lv.LvGridGenerator
import edu.ie3.osmogrid.lv.LvGridGenerator.BuildingGraphConnection
import edu.ie3.osmogrid.model.OsmTestData
import edu.ie3.test.common.UnitSpec
import edu.ie3.util.geo.GeoUtils
import edu.ie3.util.osm.model.OsmEntity.Node
import edu.ie3.util.osm.model.OsmEntity.Way.ClosedWay
import edu.ie3.util.quantities.QuantityMatchers.equalWithTolerance
import edu.ie3.util.quantities.QuantityUtils.RichQuantityDouble

import scala.collection.parallel.ParSeq

class LvGridGeneratorSpec extends UnitSpec with OsmTestData {

  "A lv grid generator spec" when {
    "building a street graph" should {
      val buildStreetGraph =
        PrivateMethod[OsmGraph](Symbol("buildStreetGraph"))
      val waySeq = ParSeq(ways.highway1, ways.highway2)
      val wayNodes = ways.highway1.nodes ++ ways.highway2.nodes
      val actual: OsmGraph =
        LvGridGenerator invokePrivate buildStreetGraph(waySeq, nodes.nodesMap)

      "build a graph with all nodes and edges" in {
        actual.vertexSet().size() shouldBe 4
        actual.edgeSet().size() shouldBe 3
        wayNodes.foreach(node =>
          actual.containsVertex(
            nodes.nodesMap.getOrElse(node, fail(f"Node: $node not found"))
          ) shouldBe true
        )
      }

      "add the correct weights to the edges" in {
        val nodeA = nodes.highway1Node1
        val nodeB = nodes.highway1Node2
        val distance = GeoUtils.calcHaversine(
          nodeA.latitude,
          nodeA.longitude,
          nodeB.latitude,
          nodeB.longitude
        )
        actual.getEdge(nodeA, nodeB).getDistance should equalWithTolerance(
          distance
        )
      }

    }

  }
  "calculating building graph connections" should {
    "calculate all building graph connections" in {
      val calcBuildingGraphConnections =
        PrivateMethod[Seq[BuildingGraphConnection]](
          Symbol("calcBuildingGraphConnections")
        )
      val buildings = Seq(ways.building1, ways.building2)
      val buildingGraphConnections: Seq[BuildingGraphConnection] =
        LvGridGenerator invokePrivate calcBuildingGraphConnections(
          Seq(ways.landuse2),
          buildings,
          Seq(ways.highway1, ways.highway2),
          nodes.nodesMap,
          0.5d.asKiloWattPerSquareMetre,
          0.0001.asKilometre
        )
      buildingGraphConnections.size shouldBe 2
      buildingGraphConnections.foreach(bgc => {
        buildings.contains(bgc.building) shouldBe true
      })
    }

    "find the closest ways to connect the buildings to" in {
      val

    }

    "calculate the power of the house connection properly" in {}

    "ignore buildings that are not inside residential areas" in {}

  }
}
