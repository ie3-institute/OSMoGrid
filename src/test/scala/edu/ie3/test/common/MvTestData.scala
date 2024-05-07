/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.test.common

import edu.ie3.datamodel.models.input.connector.LineInput
import edu.ie3.datamodel.models.input.connector.`type`.LineTypeInput
import edu.ie3.datamodel.models.input.system.characteristic.OlmCharacteristicInput
import edu.ie3.datamodel.models.input.{NodeInput, OperatorInput}
import edu.ie3.datamodel.models.voltagelevels.GermanVoltageLevelUtils
import edu.ie3.datamodel.models.{OperationTime, StandardUnits}
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.util.geo.GeoUtils
import edu.ie3.util.geo.GeoUtils.{
  buildSafeLineStringBetweenCoords,
  calcHaversine
}
import edu.ie3.util.osm.model.OsmEntity.Node
import org.locationtech.jts.geom.Coordinate
import org.scalatestplus.mockito.MockitoSugar.mock
import tech.units.indriya.quantity.Quantities
import utils.Connections
import utils.Connections.Connection
import utils.GridConversion.NodeConversion
import utils.OsmoGridUtils.getAllUniqueCombinations
import utils.Solver.StepResultOption
import edu.ie3.osmogrid.mv.VoronoiPolygonSupport.VoronoiPolygon

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
    UUID.fromString("92c3a19d-2a07-4472-bd7a-cbb49a5ae5fd"),
    s"Transition point",
    new OperatorInput(
      UUID.fromString("8d4b3c30-8622-496f-831b-9376e367c499"),
      "_"
    ),
    OperationTime.notLimited(),
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

  protected val lineHvto1: LineInput = buildLine(nodeToHv, nodeInMv1)
  protected val lineHvto2: LineInput = buildLine(nodeToHv, nodeInMv2)
  protected val line1to2: LineInput = buildLine(nodeInMv1, nodeInMv2)

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

  private def buildLine(nodeA: NodeInput, nodeB: NodeInput): LineInput =
    new LineInput(
      UUID.randomUUID(),
      s"Line between ${nodeA.getId} and ${nodeB.getId}",
      OperatorInput.NO_OPERATOR_ASSIGNED,
      OperationTime.notLimited(),
      nodeA,
      nodeB,
      1,
      mock[LineTypeInput],
      calcHaversine(
        nodeA.getGeoPosition.getCoordinate,
        nodeB.getGeoPosition.getCoordinate
      ),
      buildSafeLineStringBetweenCoords(
        nodeA.getGeoPosition.getCoordinate,
        nodeB.getGeoPosition.getCoordinate
      ),
      OlmCharacteristicInput.CONSTANT_CHARACTERISTIC
    )

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
  val connections: Connections[Node] = {
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
        val distance = calcHaversine(
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
    connections.elements.foreach { node => osmGraph.addVertex(node) }

    connections
      .getConnection(transitionPoint, osmNode1)
      .foreach(osmGraph.addConnection)
    connections
      .getConnection(transitionPoint, osmNode2)
      .foreach(osmGraph.addConnection)
    connections
      .getConnection(osmNode1, osmNode3)
      .foreach(osmGraph.addConnection)
    connections
      .getConnection(osmNode3, osmNode2)
      .foreach(osmGraph.addConnection)

    osmGraph
  }

  val stepResultOptionsForThirdStep: List[StepResultOption] = {
    val copy1: OsmGraph = graphAfterTwoSteps.copy()
    val copy2: OsmGraph = graphAfterTwoSteps.copy()

    val removedEdge1 = copy1.removeEdge(osmNode3, osmNode2)
    val usedConnections1 = List(
      connections.getConnection(osmNode2, osmNode4),
      connections.getConnection(osmNode3, osmNode4)
    ).flatten

    usedConnections1.foreach(c => copy1.addConnection(c))
    val addedWeight1 = usedConnections1(0).distance
      .add(usedConnections1(1).distance)
      .subtract(removedEdge1.getDistance)

    val removedEdge2 = copy2.removeEdge(osmNode1, osmNode3)
    val usedConnections2 = List(
      connections.getConnection(osmNode1, osmNode4),
      connections.getConnection(osmNode3, osmNode4)
    ).flatten

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

    connections.elements.foreach { n => graph.addVertex(n) }

    List(
      connections.getConnection(transitionPoint, osmNode1),
      connections.getConnection(transitionPoint, osmNode2),
      connections.getConnection(osmNode1, osmNode2),
      connections.getConnection(osmNode2, osmNode3),
      connections.getConnection(osmNode3, osmNode1)
    ).flatten.foreach(graph.addConnection)

    graph
  }
}
