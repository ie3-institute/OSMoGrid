/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.utils

import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.test.common.{MvTestData, UnitSpec}
import tech.units.indriya.quantity.Quantities
import tech.units.indriya.unit.Units
import utils.GridConversion.NodeConversion
import utils.MvUtils
import utils.MvUtils.{
  Connection,
  Connections,
  generateMvGraph,
  getAllUniqueCombinations
}
import utils.VoronoiUtils.VoronoiPolygon

import scala.jdk.CollectionConverters._
import scala.util.Try

class MvUtilsSpec extends UnitSpec with MvTestData {
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
      val createDefinitions = PrivateMethod[(NodeConversion, Connections)](
        Symbol("createDefinitions")
      )
      val nodes = List(nodeToHv, nodeInMv1, nodeInMv2)

      val (conversion, conn) =
        MvUtils invokePrivate createDefinitions(nodes, streetGraph)

      conversion.conversionToPSDM shouldBe Map(
        transitionPoint -> nodeToHv,
        osmNode1 -> nodeInMv1,
        osmNode2 -> nodeInMv2
      )

      conversion.conversionToOsm shouldBe Map(
        nodeToHv -> transitionPoint,
        nodeInMv1 -> osmNode1,
        nodeInMv2 -> osmNode2
      )

      conn.nodes shouldBe List(transitionPoint, osmNode1, osmNode2)
      conn.connections shouldBe Map(
        transitionPoint -> List(osmNode1, osmNode2),
        osmNode1 -> List(transitionPoint, osmNode2),
        osmNode2 -> List(transitionPoint, osmNode1)
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
        osmNode2
      )
      mvGraph.edgeSet().asScala shouldBe Set(
        mvGraph.getEdge(transitionPoint, osmNode1),
        mvGraph.getEdge(transitionPoint, osmNode2),
        mvGraph.getEdge(osmNode1, osmNode2)
      )

      conversion.conversionToPSDM shouldBe Map(
        transitionPoint -> nodeToHv,
        osmNode1 -> nodeInMv1,
        osmNode2 -> nodeInMv2
      )

      conversion.conversionToOsm shouldBe Map(
        nodeToHv -> transitionPoint,
        nodeInMv1 -> osmNode1,
        nodeInMv2 -> osmNode2
      )
    }

    "build unique connections correctly" in {
      val buildUniqueConnections =
        PrivateMethod[List[Connection]](Symbol("buildUniqueConnections"))
      val streetGraph = new OsmGraph()

      val uniqueCombinations =
        List((osmNode1, osmNode2), (osmNode1, osmNode3), (osmNode2, osmNode3))
      val connections = MvUtils invokePrivate buildUniqueConnections(
        uniqueCombinations,
        streetGraph
      )

      connections.size shouldBe 3

      connections(0).nodeA shouldBe osmNode1
      connections(0).nodeB shouldBe osmNode2
      connections(0).distance.getValue
        .doubleValue() shouldBe 90358.398419268055564653
      connections(0).path shouldBe None

      connections(1).nodeA shouldBe osmNode1
      connections(1).nodeB shouldBe osmNode3
      connections(1).distance.getValue
        .doubleValue() shouldBe 116699.607344808514405019
      connections(1).path shouldBe None

      connections(2).nodeA shouldBe osmNode2
      connections(2).nodeB shouldBe osmNode3
      connections(2).distance.getValue
        .doubleValue() shouldBe 170651.262486009566969106
      connections(2).path shouldBe None
    }

    "return all unique combinations correctly" in {
      val cases = Table(
        ("nodes", "combinations"),
        (List(osmNode1), List.empty),
        (List(osmNode1, osmNode2), List((osmNode1, osmNode2))),
        (
          List(osmNode1, osmNode2, osmNode3),
          List((osmNode1, osmNode2), (osmNode1, osmNode3), (osmNode2, osmNode3))
        )
      )

      forAll(cases) { (nodes, combinations) =>
        getAllUniqueCombinations(nodes) shouldBe combinations
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

    "return a connection with Double.MaxValue for not known node combinations when retrieving distance" in {
      connections.getDistance(osmNode1, osmNode6) shouldBe Quantities
        .getQuantity(Double.MaxValue, Units.METRE)
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
