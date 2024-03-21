/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.utils

import edu.ie3.datamodel.graph.DistanceWeightedEdge
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.osmogrid.lv.LvGridGeneratorSupport.GridElements
import edu.ie3.test.common.{MvTestData, UnitSpec}
import edu.ie3.util.geo.GeoUtils.calcHaversine
import edu.ie3.util.osm.model.OsmEntity.Node
import org.jgrapht.alg.shortestpath.DijkstraShortestPath
import tech.units.indriya.quantity.Quantities
import tech.units.indriya.unit.Units
import utils.Connections
import utils.Connections.{Connection, buildUniqueConnections}
import utils.OsmoGridUtils.getAllUniqueCombinations

import scala.util.{Failure, Success, Try}

class ConnectionsSpec extends UnitSpec with MvTestData {
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

    val connections: Connections[Node] = Connections(
      nodes,
      List(connection1, connection2, connection3, connection4)
    )

    "be created correctly" in {
      connections.elements shouldBe nodes
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

    "be created from GridElements correctly" in {
      val gridElements = GridElements(
        Map(osmNode1 -> nodeInMv1, osmNode2 -> nodeInMv2),
        Map(transitionPoint -> nodeToHv),
        Set.empty
      )
      val lines = Seq(lineHvto1, lineHvto2, line1to2)

      val connections = Connections(gridElements, lines)

      connections.elements shouldBe List(nodeInMv1, nodeInMv2, nodeToHv)
      connections.connectionMap.keySet shouldBe Set(
        (nodeToHv, nodeInMv1),
        (nodeInMv1, nodeToHv),
        (nodeToHv, nodeInMv2),
        (nodeInMv2, nodeToHv),
        (nodeInMv1, nodeInMv2),
        (nodeInMv2, nodeInMv1)
      )

      connections.connectionMap.values.toSet.size shouldBe 3
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
        case Failure(exception) =>
          exception.getMessage shouldBe "key not found: Node(6,50.5,8.5,Map(),None)"
        case Success(_) => fail("The test should not pass!")
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

    "logs a warning for not known node combinations when retrieving connection" in {
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

    "build unique connections correctly using shortest path " in {
      val graph = new OsmGraph()
      List(osmNode1, osmNode2, osmNode3).foreach(v => graph.addVertex(v))
      graph.addWeightedEdge(
        osmNode1,
        osmNode2,
        calcHaversine(
          osmNode1.coordinate.getCoordinate,
          osmNode2.coordinate.getCoordinate
        )
      )
      graph.addWeightedEdge(
        osmNode2,
        osmNode3,
        calcHaversine(
          osmNode2.coordinate.getCoordinate,
          osmNode3.coordinate.getCoordinate
        )
      )
      graph.addWeightedEdge(
        osmNode3,
        osmNode1,
        calcHaversine(
          osmNode3.coordinate.getCoordinate,
          osmNode1.coordinate.getCoordinate
        )
      )

      val shortestPath =
        new DijkstraShortestPath[Node, DistanceWeightedEdge](graph)
      val connections: List[Connection[Node]] =
        buildUniqueConnections(graph, shortestPath)

      connections.size shouldBe 3

      connections(0).nodeA shouldBe osmNode1
      connections(0).nodeB shouldBe osmNode2
      connections(0).distance.getValue
        .doubleValue() shouldBe 90358.398419268055564653
      connections(0).path.isDefined shouldBe true

      connections(1).nodeA shouldBe osmNode1
      connections(1).nodeB shouldBe osmNode3
      connections(1).distance.getValue
        .doubleValue() shouldBe 116699.607344808514405019
      connections(1).path.isDefined shouldBe true

      connections(2).nodeA shouldBe osmNode2
      connections(2).nodeB shouldBe osmNode3
      connections(2).distance.getValue
        .doubleValue() shouldBe 170651.262486009566969106
      connections(2).path.isDefined shouldBe true
    }

    "fail to build unique connections if the graph contains unconnected vertexes" in {
      val graph = new OsmGraph()
      List(osmNode1, osmNode2, osmNode3).foreach(v => graph.addVertex(v))
      graph.addWeightedEdge(
        osmNode1,
        osmNode2,
        calcHaversine(
          osmNode1.coordinate.getCoordinate,
          osmNode2.coordinate.getCoordinate
        )
      )

      val shortestPath =
        new DijkstraShortestPath[Node, DistanceWeightedEdge](graph)

      Try(buildUniqueConnections(graph, shortestPath)) match {
        case Failure(exception) =>
          exception.getMessage == "No path could be found between Node(1,50.5,7.0,Map(),None) and Node(3,51.5,7.5,Map(),None), because the node Node(3,51.5,7.5,Map(),None) is not connected to the graph."
        case Success(_) => fail("This test should not succeed.")
      }
    }

    "build unique connections correctly" in {
      val streetGraph = new OsmGraph()

      val uniqueCombinations =
        List((osmNode1, osmNode2), (osmNode1, osmNode3), (osmNode2, osmNode3))
      val connections = buildUniqueConnections(
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
