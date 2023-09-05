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
import utils.GraphUtils

import java.util.Random

object SolverTester {
  def main(args: Array[String]): Unit = {
    val graph = generateGraph()

    GraphUtils.draw(graph, "graph.png")
  }

  def generateGraph(): OsmGraph = {
    val (transitionPoint, connections) = generatePoints(5)
    val graph: OsmGraph = Solver.solve(transitionPoint, connections)

    graph.edgeSet().forEach { e =>
      System.out.print(
        s"\n${graph.getEdgeSource(e).id}, ${graph.getEdgeTarget(e).id}, ${e.getDistance.getValue.doubleValue()}"
      )
    }

    graph
  }

  // generates some random points
  private def generatePoints(n: Int): (Node, Connections) = {
    val transitionPoint = Node(
      id = 0L,
      latitude = 50.0,
      longitude = 7.0,
      tags = Map.empty,
      metaInformation = None
    )
    val list: List[Node] = Range.Int
      .inclusive(1, n, 1)
      .map { i => randomNode(i) }
      .toList :+ transitionPoint

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

  private def randomNode(i: Int): Node = {
    val rand = new Random()
    val lat = 50d
    val lon = 7d

    val one: Double = rand.nextInt(100) / 10d
    val two: Double = rand.nextInt(100) / 10d
    Node(i, lat + one, lon + two, Map.empty, None)
  }
}
