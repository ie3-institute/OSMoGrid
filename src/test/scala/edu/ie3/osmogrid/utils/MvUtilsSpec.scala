/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.utils

import edu.ie3.test.common.{MvTestData, UnitSpec}
import edu.ie3.util.osm.model.OsmEntity.Node
import tech.units.indriya.quantity.Quantities
import tech.units.indriya.unit.Units
import utils.MvUtils.{Connection, Connections, getAllUniqueCombinations}

import scala.util.Try

class MvUtilsSpec extends UnitSpec with MvTestData {
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

  "The Connections" should {
    val nodes = List(transitionPoint, osmNode1, osmNode2, osmNode3)
    val connection1 = Connection(
      transitionPoint,
      osmNode1,
      Quantities.getQuantity(0d, Units.METRE),
      None
    )
    val connection2 = Connection(
      transitionPoint,
      osmNode2,
      Quantities.getQuantity(0d, Units.METRE),
      None
    )
    val connection3 = Connection(
      osmNode1,
      osmNode3,
      Quantities.getQuantity(0d, Units.METRE),
      None
    )
    val connection4 = Connection(
      osmNode3,
      osmNode2,
      Quantities.getQuantity(0d, Units.METRE),
      None
    )

    val connections: Connections = Connections(
      nodes,
      List(connection1, connection2, connection3, connection4)
    )

    "be created correctly" in {
      connections.nodes shouldBe nodes
      connections.connections shouldBe Map(
        transitionPoint -> List(osmNode1, osmNode2),
        osmNode1 -> List(transitionPoint, osmNode3),
        osmNode2 -> List(transitionPoint, osmNode3),
        osmNode3 -> List(osmNode1, osmNode2)
      )

      connections.connectionMap shouldBe Map(
        (transitionPoint, osmNode1) -> connection1,
        (transitionPoint, osmNode2) -> connection2,
        (osmNode1, transitionPoint) -> connection1,
        (osmNode1, osmNode3) -> connection3,
        (osmNode2, transitionPoint) -> connection2,
        (osmNode2, osmNode3) -> connection4,
        (osmNode3, osmNode1) -> connection3,
        (osmNode3, osmNode2) -> connection4
      )
    }

    "return all connections" in {
      val cases = Table(
        ("node", "expectedConnections"),
        (transitionPoint, List(connection1, connection2)),
        (osmNode1, List(connection1, connection3)),
        (osmNode2, List(connection2, connection4)),
        (osmNode3, List(connection3, connection4))
      )

      forAll(cases) { (node, expectedConnection) =>
        connections.getConnections(node) shouldBe expectedConnection
      }
    }

    "throws an exception for not known nodes" in {
      Try(connections.getConnections(osmNode6)) match {
        case util.Failure(exception) =>
          exception.getMessage shouldBe "key not found: Node(6,50.5,8.5,Map(),None)"
        case util.Success(_) => throw new Error("The test should not pass!")
      }
    }

    "return a specific connection" in {
      val cases = Table(
        ("nodeA", "nodeB", "connection"),
        (transitionPoint, osmNode1, connection1),
        (osmNode1, transitionPoint, connection1),
        (transitionPoint, osmNode2, connection2)
      )

      forAll(cases) { (nodeA, nodeB, connection) =>
        connections.getConnection(nodeA, nodeB) shouldBe connection
      }
    }

    "throws an exception for not known node combinations when retrieving connection" in {
      val notFound = connections.getConnection(osmNode1, osmNode6)

      notFound.nodeA shouldBe osmNode1
      notFound.nodeB shouldBe osmNode6
      notFound.distance.getValue.doubleValue() shouldBe Double.MaxValue
      notFound.path shouldBe None
    }

    "return the correct distance between tow nodes" in {
      val cases = Table(
        ("nodeA", "nodeB", "distance"),
        (transitionPoint, osmNode1, connection1.distance),
        (osmNode1, transitionPoint, connection1.distance),
        (transitionPoint, osmNode2, connection2.distance)
      )

      forAll(cases) { (nodeA, nodeB, distance) =>
        connections.getDistance(nodeA, nodeB) shouldBe distance
      }
    }

    "throws an exception for not known node combinations when retrieving distance" in {
      Try(connections.getDistance(osmNode1, osmNode6)) match {
        case util.Failure(exception) =>
          exception.getMessage shouldBe "key not found: (Node(6,50.5,8.5,Map(),None),Node(1,50.5,7.0,Map(),None))"
        case util.Success(_) => throw new Error("The test should not pass!")
      }
    }

    "return the nearest neighbors for a given node" in {
      val cases = Table(
        ("node", "nearestNeighbours"),
        (transitionPoint, List(osmNode1, osmNode2)),
        (osmNode1, List(transitionPoint, osmNode3)),
        (osmNode2, List(transitionPoint, osmNode3)),
        (osmNode3, List(osmNode1, osmNode2))
      )

      forAll(cases) { (node, nearestNeighbours) =>
        connections.getNearestNeighbors(node) shouldBe nearestNeighbours
      }
    }

    "return the nearest n neighbors for a given node" in {
      val cases = Table(
        ("node", "n", "nearestNeighbours"),
        (transitionPoint, 1, List(osmNode1)),
        (osmNode1, 2, List(transitionPoint, osmNode3)),
        (osmNode2, 1, List(transitionPoint)),
        (osmNode3, 2, List(osmNode1, osmNode2))
      )

      forAll(cases) { (node, n, nearestNeighbours) =>
        connections.getNearestNeighbors(node, n) shouldBe nearestNeighbours
      }
    }

    "return all unique combinations correctly" in {

      val cases = Table(
        ("nodes", "combinations"),
        (List(transitionPoint, osmNode1), List((transitionPoint, osmNode1))),
        (
          List(transitionPoint, osmNode1, osmNode2),
          List(
            (transitionPoint, osmNode1),
            (transitionPoint, osmNode2),
            (osmNode1, osmNode2)
          )
        ),
        (
          List(
            transitionPoint,
            osmNode1,
            osmNode2,
            osmNode3,
            osmNode4,
            osmNode5,
            osmNode6
          ),
          List(
            (transitionPoint, osmNode1),
            (transitionPoint, osmNode2),
            (transitionPoint, osmNode3),
            (transitionPoint, osmNode4),
            (transitionPoint, osmNode5),
            (transitionPoint, osmNode6),
            (osmNode1, osmNode2),
            (osmNode1, osmNode3),
            (osmNode1, osmNode4),
            (osmNode1, osmNode5),
            (osmNode1, osmNode6),
            (osmNode2, osmNode3),
            (osmNode2, osmNode4),
            (osmNode2, osmNode5),
            (osmNode2, osmNode6),
            (osmNode3, osmNode4),
            (osmNode3, osmNode5),
            (osmNode3, osmNode6),
            (osmNode4, osmNode5),
            (osmNode4, osmNode6),
            (osmNode5, osmNode6)
          )
        )
      )

      forAll(cases) { (nodes, combinations) =>
        getAllUniqueCombinations(nodes) shouldBe combinations
      }
    }
  }
}
