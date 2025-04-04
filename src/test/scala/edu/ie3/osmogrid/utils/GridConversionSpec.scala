/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.utils

import edu.ie3.datamodel.models.StandardUnits
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.test.common.{GridSupport, MvTestData, UnitSpec}
import edu.ie3.util.osm.model.OsmEntity.Node
import tech.units.indriya.quantity.Quantities
import tech.units.indriya.unit.Units
import utils.GridConversion._
import utils.Solver

import scala.util.Try

class GridConversionSpec extends UnitSpec with MvTestData with GridSupport {
  "The GridConversion" should {
    val baseGraph: OsmGraph = Solver.solve(transitionPoint, connections)

    "build a mv line input correctly" in {
      val cases = Table(
        ("id", "nodeA", "nodeB", "parallel", "lineType", "length"),
        ("1", nodeInMv1, nodeInMv2, 1, defaultLineTypeMv, 500),
      )

      forAll(cases) { (id, nodeA, nodeB, parallel, lineType, length) =>
        val line = buildLine(
          id,
          nodeA,
          nodeB,
          parallel,
          lineType,
          Quantities.getQuantity(length, Units.METRE),
        )

        line.getId shouldBe id
        line.getNodeA shouldBe nodeA
        line.getNodeB shouldBe nodeB
        line.getParallelDevices shouldBe parallel
        line.getType shouldBe lineType
        line.getLength.getUnit shouldBe StandardUnits.LINE_LENGTH
        line.getLength.getValue.doubleValue() shouldBe length / 1000.0
        line.getGeoPosition.getStartPoint shouldBe nodeA.getGeoPosition
        line.getGeoPosition.getEndPoint shouldBe nodeB.getGeoPosition

      }
    }
  }

  "The NodeConversion" should {
    "can be applied correctly" in {
      val nodes = List(nodeToHv, nodeInMv2, nodeInMv3, nodeInMv6)
      val osmNodes = List(
        transitionPoint,
        osmNode1,
        osmNode2,
        osmNode3,
        osmNode4,
        osmNode5,
        osmNode6,
      )

      val nodeConversion = NodeConversion(nodes, osmNodes)

      nodeConversion.allPsdmNodes shouldBe nodes
      nodeConversion.allOsmNodes shouldBe List(
        transitionPoint,
        osmNode2,
        osmNode3,
        osmNode6,
      )

      nodeConversion.conversionToOsm shouldBe Map(
        nodeToHv -> transitionPoint,
        nodeInMv2 -> osmNode2,
        nodeInMv3 -> osmNode3,
        nodeInMv6 -> osmNode6,
      )
    }

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
          nodeInMv6,
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
          osmNode6,
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
        (nodeInMv6, osmNode6),
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
        nodeInMv6,
      )
      nodeConversion.getOsmNodes(psdmNodes) shouldBe List(
        transitionPoint,
        osmNode1,
        osmNode2,
        osmNode3,
        osmNode4,
        osmNode5,
        osmNode6,
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
        (osmNode6, nodeInMv6),
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
        osmNode6,
      )
      nodeConversion.getPSDMNodes(osmNodes) shouldBe List(
        nodeToHv,
        nodeInMv1,
        nodeInMv2,
        nodeInMv3,
        nodeInMv4,
        nodeInMv5,
        nodeInMv6,
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
            metaInformation = None,
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
