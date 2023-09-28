/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.utils

import edu.ie3.datamodel.models.input.system.characteristic.OlmCharacteristicInput._
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.test.common.{MvTestData, UnitSpec}
import edu.ie3.util.osm.model.OsmEntity.Node
import utils.GridConversion._
import utils.Solver

import scala.util.Try

class GridConversionSpec extends UnitSpec with MvTestData {
  "The GridConversion" should {
    val baseGraph: OsmGraph = Solver.solve(transitionPoint, connections)

    "convert mv grids correctly" in {
      val graph = baseGraph.copy()
      val (nodes, lines) = convertMv(2, graph, nodeConversion)

      nodes shouldBe Set(
        nodeToHv,
        nodeInMv1,
        nodeInMv2,
        nodeInMv3,
        nodeInMv4,
        nodeInMv5,
        nodeInMv6
      )
      lines.size shouldBe 7

      lines.foreach { line =>
        line.getParallelDevices shouldBe 1
        line.getType shouldBe defaultLineType_10kV
        line.getGeoPosition.getStartPoint shouldBe line.getNodeA.getGeoPosition
        line.getGeoPosition.getEndPoint shouldBe line.getNodeB.getGeoPosition
        line.getOlmCharacteristic shouldBe CONSTANT_CHARACTERISTIC
      }
    }
  }

  "The NodeConversion" should {
    "return all psdm nodes correctly" in {
      val allNodes = nodeConversion.allPsdmNodes
      allNodes.size shouldBe 7

      allNodes.diff(
        List(
          nodeToHv,
          nodeInMv1,
          nodeInMv2,
          nodeInMv3,
          nodeInMv4,
          nodeInMv5,
          nodeInMv6
        )
      ) shouldBe List.empty
    }

    "return all osm nodes correctly" in {
      val allNodes = nodeConversion.allOsmNodes
      allNodes.size shouldBe 7

      allNodes.diff(
        List(
          transitionPoint,
          osmNode1,
          osmNode2,
          osmNode3,
          osmNode4,
          osmNode5,
          osmNode6
        )
      ) shouldBe List.empty
    }

    "return the corresponding osm node for a given PSDM node" in {
      val cases = Table(
        ("psdmNode", "osmNode"),
        (nodeToHv, transitionPoint),
        (nodeInMv1, osmNode1),
        (nodeInMv2, osmNode2),
        (nodeInMv3, osmNode3),
        (nodeInMv4, osmNode4),
        (nodeInMv5, osmNode5),
        (nodeInMv6, osmNode6)
      )

      forAll(cases) { (psdmNode, osmNode) =>
        nodeConversion.getOsmNode(psdmNode) shouldBe osmNode
      }
    }

    "return the corresponding osm nodes for given PSDM nodes" in {
      val psdmNodes = List(
        nodeToHv,
        nodeInMv1,
        nodeInMv2,
        nodeInMv3,
        nodeInMv4,
        nodeInMv5,
        nodeInMv6
      )
      nodeConversion.getOsmNodes(psdmNodes) shouldBe List(
        transitionPoint,
        osmNode1,
        osmNode2,
        osmNode3,
        osmNode4,
        osmNode5,
        osmNode6
      )
    }

    "return the corresponding PSDM node for a given osm node" in {
      val cases = Table(
        ("osmNode", "psdmNode"),
        (transitionPoint, nodeToHv),
        (osmNode1, nodeInMv1),
        (osmNode2, nodeInMv2),
        (osmNode3, nodeInMv3),
        (osmNode4, nodeInMv4),
        (osmNode5, nodeInMv5),
        (osmNode6, nodeInMv6)
      )

      forAll(cases) { (osmNode, psdmNode) =>
        nodeConversion.getPSDMNode(osmNode) shouldBe psdmNode
      }
    }

    "return the corresponding PSDM nodes for given osm nodes" in {
      val osmNodes = List(
        transitionPoint,
        osmNode1,
        osmNode2,
        osmNode3,
        osmNode4,
        osmNode5,
        osmNode6
      )
      nodeConversion.getPSDMNodes(osmNodes) shouldBe List(
        nodeToHv,
        nodeInMv1,
        nodeInMv2,
        nodeInMv3,
        nodeInMv4,
        nodeInMv5,
        nodeInMv6
      )
    }

    "throws an exception if a given node is not found" in {
      Try(
        nodeConversion.getPSDMNode(
          Node(
            id = 7L,
            latitude = 0.0,
            longitude = 0.0,
            tags = Map.empty,
            metaInformation = None
          )
        )
      ) match {
        case util.Failure(exception) =>
          exception.getMessage shouldBe "key not found: Node(7,0.0,0.0,Map(),None)"
        case util.Success(_) => throw new Error("The test should not pass!")
      }
    }
  }
}
