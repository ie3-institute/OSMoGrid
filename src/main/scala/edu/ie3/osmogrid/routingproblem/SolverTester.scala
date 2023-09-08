/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.routingproblem

import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.osmogrid.routingproblem.Definitions.{Connection, Connections}
import edu.ie3.util.geo.GeoUtils
import edu.ie3.util.osm.model.OsmEntity.Node
import org.locationtech.jts.geom.Coordinate
import utils.GraphUtils

import java.time.Instant
import java.util.Random

object SolverTester {
  private val DEFAULT_COORDINATE = new Coordinate(7d, 50d)

  def main(args: Array[String]): Unit = {
    GraphUtils.FOLDER.toFile.listFiles().foreach { f => f.delete() }
    val coordinates: List[Coordinate] = generateCoordinates(50)

    val start = Instant.now().toEpochMilli
    val graph = generateGraph(case50n)
    System.out.print(s"\n\nTime needed: ${Instant.now().toEpochMilli - start}.")

    GraphUtils.draw(graph, "graph.png", 800, 600)
  }

  val default: List[Coordinate] = List(
    new Coordinate(7.0, 50.5),
    new Coordinate(8.0, 50.0),
    new Coordinate(7.5, 51.5),
    new Coordinate(8.5, 51.5),
    new Coordinate(9.0, 51.0),
    new Coordinate(8.5, 50.5)
  )

  val problem_case1: List[Coordinate] = List(
    new Coordinate(9.85, 50.7),
    new Coordinate(10.1, 51.25),
    new Coordinate(11.65, 51.0),
    new Coordinate(7.75, 52.85),
    new Coordinate(7.4, 54.15),
    new Coordinate(11.05, 53.2),
    new Coordinate(10.6, 52.0),
    new Coordinate(9.9, 54.9),
    new Coordinate(8.75, 51.7),
    new Coordinate(11.3, 51.5),
    new Coordinate(7.15, 51.75),
    new Coordinate(11.6, 52.3),
    new Coordinate(11.6, 53.4),
    new Coordinate(8.15, 51.0),
    new Coordinate(11.45, 54.3),
    new Coordinate(8.8, 53.65),
    new Coordinate(9.3, 52.0),
    new Coordinate(10.4, 50.0),
    new Coordinate(8.75, 52.15),
    new Coordinate(10.75, 52.1)
  )

  val problem_case2: List[Coordinate] = List(
    new Coordinate(10.0, 52.5),
    new Coordinate(7.35, 53.3),
    new Coordinate(11.45, 50.65),
    new Coordinate(7.7, 53.8),
    new Coordinate(7.45, 52.05),
    new Coordinate(10.45, 52.95),
    new Coordinate(8.1, 50.4),
    new Coordinate(9.9, 50.45),
    new Coordinate(11.85, 50.3),
    new Coordinate(7.8, 52.55),
    new Coordinate(9.5, 51.3),
    new Coordinate(8.5, 50.35),
    new Coordinate(7.4, 51.1),
    new Coordinate(9.0, 50.45),
    new Coordinate(9.95, 51.1)
  )

  def problem_case3: List[Coordinate] = List(
    new Coordinate(2.35, 54.1),
    new Coordinate(11.3, 51.0),
    new Coordinate(4.85, 54.9),
    new Coordinate(9.65, 50.45),
    new Coordinate(5.05, 50.5),
    new Coordinate(11.25, 52.9),
    new Coordinate(3.7, 52.7),
    new Coordinate(11.85, 51.3),
    new Coordinate(8.65, 50.6),
    new Coordinate(9.7, 51.7),
    new Coordinate(7.6, 50.6),
    new Coordinate(8.55, 54.75),
    new Coordinate(5.9, 53.25),
    new Coordinate(6.2, 51.8),
    new Coordinate(4.85, 50.15),
    new Coordinate(5.1, 54.55),
    new Coordinate(6.2, 54.95),
    new Coordinate(11.3, 54.9),
    new Coordinate(10.55, 54.25),
    new Coordinate(3.7, 51.4)
  )

  val case1: List[Coordinate] = List(
    new Coordinate(7.85, 51.4),
    new Coordinate(11.2, 54.1),
    new Coordinate(8.45, 50.15),
    new Coordinate(11.45, 50.7),
    new Coordinate(9.25, 51.7),
    new Coordinate(10.55, 52.9),
    new Coordinate(7.25, 53.95),
    new Coordinate(7.95, 50.4),
    new Coordinate(9.1, 52.15),
    new Coordinate(8.55, 50.7)
  )

  val case2: List[Coordinate] = List(
    new Coordinate(10.95, 50.1),
    new Coordinate(9.95, 54.05),
    new Coordinate(10.8, 52.65),
    new Coordinate(11.15, 52.4),
    new Coordinate(9.7, 52.15),
    new Coordinate(11.4, 54.25),
    new Coordinate(8.45, 50.5),
    new Coordinate(8.35, 50.95),
    new Coordinate(10.35, 53.2),
    new Coordinate(10.15, 53.55)
  )

  val case50n: List[Coordinate] = List(
    new Coordinate(7.7, 52.05),
    new Coordinate(5.9, 50.55),
    new Coordinate(9.55, 50.7),
    new Coordinate(10.5, 50.0),
    new Coordinate(2.2, 53.25),
    new Coordinate(4.7, 54.75),
    new Coordinate(2.5, 52.45),
    new Coordinate(8.75, 50.65),
    new Coordinate(11.8, 52.0),
    new Coordinate(6.3, 54.7),
    new Coordinate(6.5, 53.8),
    new Coordinate(6.35, 50.85),
    new Coordinate(6.5, 51.1),
    new Coordinate(4.55, 50.55),
    new Coordinate(11.9, 52.45),
    new Coordinate(2.45, 54.35),
    new Coordinate(10.8, 53.45),
    new Coordinate(3.3, 50.85),
    new Coordinate(6.85, 54.55),
    new Coordinate(5.3, 53.3),
    new Coordinate(9.45, 51.3),
    new Coordinate(3.1, 54.7),
    new Coordinate(4.3, 52.85),
    new Coordinate(5.05, 53.55),
    new Coordinate(4.15, 54.3),
    new Coordinate(6.6, 52.8),
    new Coordinate(6.8, 54.85),
    new Coordinate(7.35, 51.35),
    new Coordinate(4.3, 50.25),
    new Coordinate(5.05, 53.25),
    new Coordinate(7.5, 54.65),
    new Coordinate(3.25, 51.2),
    new Coordinate(10.0, 54.9),
    new Coordinate(4.7, 51.95),
    new Coordinate(5.6, 52.55),
    new Coordinate(9.45, 51.6),
    new Coordinate(7.6, 52.8),
    new Coordinate(6.95, 52.05),
    new Coordinate(9.9, 52.6),
    new Coordinate(6.3, 51.95),
    new Coordinate(10.65, 52.2),
    new Coordinate(3.9, 52.35),
    new Coordinate(8.7, 54.75),
    new Coordinate(9.4, 53.85),
    new Coordinate(6.55, 53.95),
    new Coordinate(10.5, 54.65),
    new Coordinate(6.4, 52.45),
    new Coordinate(7.75, 50.45),
    new Coordinate(8.05, 54.5),
    new Coordinate(6.2, 54.1)
  )

  private def generateGraph(coordinates: List[Coordinate]): OsmGraph = {
    val (transitionPoint, connections) = generatePoints(coordinates)
    val graph: OsmGraph = Solver.solve(transitionPoint, connections)

    graph.edgeSet().forEach { e =>
      System.out.print(
        s"\n${graph.getEdgeSource(e).id}, ${graph.getEdgeTarget(e).id}, ${e.getDistance.getValue.doubleValue()}"
      )
    }

    graph
  }

  // generates some random points
  private def generatePoints(
      coordinates: List[Coordinate]
  ): (Node, Connections) = {
    val transitionPoint = Node(
      id = 0L,
      latitude = DEFAULT_COORDINATE.y,
      longitude = DEFAULT_COORDINATE.x,
      tags = Map.empty,
      metaInformation = None
    )

    val list: List[Node] = coordinates.zipWithIndex.map {
      case (coordinate, i) =>
        Node(i + 1, coordinate.y, coordinate.x, Map.empty, None)
    } :+ transitionPoint

    val uniqueConnections = Connections
      .getAllUniqueCombinations(list)
      .map { case (nodeA, nodeB) =>
        val distance = GeoUtils.calcHaversine(
          nodeA.coordinate.getCoordinate,
          nodeB.coordinate.getCoordinate
        )
        Connection(nodeA, nodeB, distance, None)
      }

    list.foreach { v =>
      System.out.print(s"\n${v.id}, ${v.longitude}, ${v.latitude}")
    }

    (transitionPoint, Connections(list, uniqueConnections))
  }

  private def generateCoordinates(n: Int): List[Coordinate] = {
    Range.Int
      .inclusive(1, n, 1)
      .map { _ =>
        val rand = new Random()
        val bool = rand.nextBoolean()
        val dLat: Double = rand.nextInt(100) / 20d
        val dLon: Double = rand.nextInt(100) / 20d

        if (bool) {
          new Coordinate(
            DEFAULT_COORDINATE.x - dLon,
            DEFAULT_COORDINATE.y + dLat
          )
        } else {
          new Coordinate(
            DEFAULT_COORDINATE.x + dLon,
            DEFAULT_COORDINATE.y + dLat
          )
        }
      }
      .toList
  }
}
