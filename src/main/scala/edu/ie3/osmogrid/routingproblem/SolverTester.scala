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
    val coordinates: List[Coordinate] = generateCoordinates(20)

    val start = Instant.now().toEpochMilli
    val graph = generateGraph(coordinates)
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
