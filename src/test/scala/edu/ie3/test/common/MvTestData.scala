/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.test.common

import edu.ie3.datamodel.models.StandardUnits
import edu.ie3.datamodel.models.input.NodeInput
import edu.ie3.datamodel.models.voltagelevels.GermanVoltageLevelUtils
import edu.ie3.osmogrid.graph.OsmGraph
import utils.GridConversion.NodeConversion
import utils.Solver.StepResultOption
import edu.ie3.util.geo.GeoUtils
import edu.ie3.util.osm.model.OsmEntity.Node
import org.locationtech.jts.geom.Coordinate
import tech.units.indriya.quantity.Quantities
import utils.MvUtils.{Connection, Connections, getAllUniqueCombinations}
import utils.VoronoiUtils.VoronoiPolygon

import java.util.UUID

trait MvTestData {
  // test grid
  // ---- |  7  7.5  8  8.5  9
  // 52.0 |
  // 51.5 |      3       4
  // 51.0 |                  5
  // 50.5 |  1           6
  // 50.0 |  0       2

  // corresponding osm and PSDM nodes
  protected val transitionPoint: Node = Node(
    id = 0L,
    latitude = 50.0,
    longitude = 7.0,
    tags = Map.empty,
    metaInformation = None
  )
  val nodeToHv = new NodeInput(
    UUID.randomUUID(),
    s"Transition point",
    Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
    true,
    GeoUtils.buildPoint(50.0, 7.0),
    GermanVoltageLevelUtils.MV_10KV,
    1
  )

  protected val (osmNode1, nodeInMv1) = buildPoint(1L, 50.5, 7.0)
  protected val (osmNode2, nodeInMv2) = buildPoint(2L, 50.0, 8.0)
  protected val (osmNode3, nodeInMv3) = buildPoint(3L, 51.5, 7.5)
  protected val (osmNode4, nodeInMv4) = buildPoint(4L, 51.5, 8.5)
  protected val (osmNode5, nodeInMv5) = buildPoint(5L, 51.0, 9.0)
  protected val (osmNode6, nodeInMv6) = buildPoint(6L, 50.5, 8.5)

  def buildPoint(i: Long, lat: Double, lon: Double): (Node, NodeInput) = {
    val node = Node(i, lat, lon, Map.empty, None)
    val nodeInput = new NodeInput(
      UUID.randomUUID(),
      s"Node $i",
      Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
      false,
      GeoUtils.buildPoint(lat, lon),
      GermanVoltageLevelUtils.MV_10KV,
      1
    )
    (node, nodeInput)
  }

  def toCoordinate(node: Node): Coordinate =
    GeoUtils.buildCoordinate(node.latitude, node.longitude)

  val nodeConversion: NodeConversion = {
    val conversion = Map(
      nodeToHv -> transitionPoint,
      nodeInMv1 -> osmNode1,
      nodeInMv2 -> osmNode2,
      nodeInMv3 -> osmNode3,
      nodeInMv4 -> osmNode4,
      nodeInMv5 -> osmNode5,
      nodeInMv6 -> osmNode6
    )

    NodeConversion(conversion, conversion.map { case (k, v) => v -> k })
  }
  val connections: Connections = {
    val uniqueConnections = getAllUniqueCombinations(
      List(
        transitionPoint,
        osmNode1,
        osmNode2,
        osmNode3,
        osmNode4,
        osmNode5,
        osmNode6
      )
    )
      .map { case (nodeA, nodeB) =>
        val distance = GeoUtils.calcHaversine(
          nodeA.coordinate.getCoordinate,
          nodeB.coordinate.getCoordinate
        )
        Connection(nodeA, nodeB, distance, None)
      }

    val osmNodes = List(
      transitionPoint,
      osmNode1,
      osmNode2,
      osmNode3,
      osmNode4,
      osmNode5,
      osmNode6
    )

    Connections(osmNodes, uniqueConnections)
  }

  val graphAfterTwoSteps: OsmGraph = {
    val osmGraph: OsmGraph = new OsmGraph()
    connections.nodes.foreach { node => osmGraph.addVertex(node) }
    osmGraph.addConnection(
      connections.getConnection(transitionPoint, osmNode1)
    )
    osmGraph.addConnection(
      connections.getConnection(transitionPoint, osmNode2)
    )
    osmGraph.addConnection(connections.getConnection(osmNode1, osmNode3))
    osmGraph.addConnection(connections.getConnection(osmNode3, osmNode2))

    osmGraph
  }

  val stepResultOptionsForThirdStep: List[StepResultOption] = {
    val copy1: OsmGraph = graphAfterTwoSteps.copy()
    val copy2: OsmGraph = graphAfterTwoSteps.copy()

    val removedEdge1 = copy1.removeEdge(osmNode3, osmNode2)
    val usedConnections1 = List(
      connections.getConnection(osmNode2, osmNode4),
      connections.getConnection(osmNode3, osmNode4)
    )
    usedConnections1.foreach(c => copy1.addConnection(c))
    val addedWeight1 = usedConnections1(0).distance
      .add(usedConnections1(1).distance)
      .subtract(removedEdge1.getDistance)

    val removedEdge2 = copy2.removeEdge(osmNode1, osmNode3)
    val usedConnections2 = List(
      connections.getConnection(osmNode1, osmNode4),
      connections.getConnection(osmNode3, osmNode4)
    )
    usedConnections2.foreach(c => copy2.addConnection(c))
    val addedWeight2 = usedConnections2(0).distance
      .add(usedConnections2(1).distance)
      .subtract(removedEdge2.getDistance)

    List(
      StepResultOption(
        copy1,
        osmNode4,
        usedConnections1,
        graphAfterTwoSteps.getEdge(osmNode3, osmNode2),
        addedWeight1
      ),
      StepResultOption(
        copy2,
        osmNode4,
        usedConnections2,
        graphAfterTwoSteps.getEdge(osmNode1, osmNode3),
        addedWeight2
      )
    )
  }

  val polygon: VoronoiPolygon =
    VoronoiPolygon(nodeToHv, List(nodeInMv1, nodeInMv2), None)

  val streetGraph: OsmGraph = {
    val graph = new OsmGraph()

    connections.nodes.foreach { n => graph.addVertex(n) }

    List(
      connections.getConnection(transitionPoint, osmNode1),
      connections.getConnection(transitionPoint, osmNode2),
      connections.getConnection(osmNode1, osmNode2),
      connections.getConnection(osmNode2, osmNode3),
      connections.getConnection(osmNode3, osmNode1)
    ).foreach { c => graph.addConnection(c) }

    graph
  }
}