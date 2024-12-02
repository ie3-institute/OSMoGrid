/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.mv

import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.osmogrid.mv.MvGraphGeneratorSupport.generateMvGraph
import edu.ie3.osmogrid.mv.VoronoiPolygonSupport.VoronoiPolygon
import edu.ie3.test.common.{MvTestData, UnitSpec}
import edu.ie3.util.osm.model.OsmEntity.Node
import utils.Connections
import utils.GridConversion.NodeConversion

import scala.jdk.CollectionConverters._

class MvGraphGeneratorSupportSpec extends UnitSpec with MvTestData {
  "The MvUtils" should {
    def streetGraph: OsmGraph = {
      val streetGraph = new OsmGraph()
      streetGraph.addVertex(transitionPoint)
      streetGraph.addVertex(osmNode1)
      streetGraph.addVertex(osmNode2)
      streetGraph.addVertex(osmNode3)
      streetGraph.addEdge(transitionPoint, osmNode1)
      streetGraph.addEdge(transitionPoint, osmNode2)
      streetGraph.addEdge(osmNode1, osmNode2)
      streetGraph.addEdge(osmNode1, osmNode3)

      streetGraph
    }

    "create definitions correctly" in {
      val createDefinitions =
        PrivateMethod[(NodeConversion, Connections[Node])](
          Symbol("createDefinitions")
        )
      val nodes = List(nodeToHv, nodeInMv1, nodeInMv2)

      val (conversion, conn) =
        MvGraphGeneratorSupport invokePrivate createDefinitions(
          nodes,
          streetGraph,
        )

      conversion.conversionToPSDM shouldBe Map(
        transitionPoint -> nodeToHv,
        osmNode1 -> nodeInMv1,
        osmNode2 -> nodeInMv2,
      )

      conversion.conversionToOsm shouldBe Map(
        nodeToHv -> transitionPoint,
        nodeInMv1 -> osmNode1,
        nodeInMv2 -> osmNode2,
      )

      conn.elements shouldBe List(transitionPoint, osmNode1, osmNode2)
      conn.connections shouldBe Map(
        transitionPoint -> List(osmNode1, osmNode2),
        osmNode1 -> List(transitionPoint, osmNode2),
        osmNode2 -> List(transitionPoint, osmNode1),
      )

      conn.connectionMap.contains((transitionPoint, osmNode1)) shouldBe true
      conn.connectionMap.contains((transitionPoint, osmNode2)) shouldBe true
      conn.connectionMap.contains((osmNode1, transitionPoint)) shouldBe true
      conn.connectionMap.contains((osmNode1, osmNode2)) shouldBe true
      conn.connectionMap.contains((osmNode2, transitionPoint)) shouldBe true
      conn.connectionMap.contains((osmNode2, osmNode1)) shouldBe true
    }

    "generate mv graph correctly" in {
      val voronoiPolygon =
        VoronoiPolygon(nodeToHv, List(nodeInMv1, nodeInMv2), None)

      val (mvGraph, conversion) =
        generateMvGraph(1, voronoiPolygon, streetGraph)

      mvGraph.vertexSet().asScala shouldBe Set(
        transitionPoint,
        osmNode1,
        osmNode2,
      )
      mvGraph.edgeSet().asScala shouldBe Set(
        mvGraph.getEdge(transitionPoint, osmNode1),
        mvGraph.getEdge(transitionPoint, osmNode2),
        mvGraph.getEdge(osmNode1, osmNode2),
      )

      conversion.conversionToPSDM shouldBe Map(
        transitionPoint -> nodeToHv,
        osmNode1 -> nodeInMv1,
        osmNode2 -> nodeInMv2,
      )

      conversion.conversionToOsm shouldBe Map(
        nodeToHv -> transitionPoint,
        nodeInMv1 -> osmNode1,
        nodeInMv2 -> osmNode2,
      )
    }
  }
}
