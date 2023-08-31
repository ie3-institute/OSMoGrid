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
import edu.ie3.osmogrid.routingproblem.Definitions.{
  Connection,
  Connections,
  NodeConversion,
  StepResultOption
}
import edu.ie3.util.geo.GeoUtils
import edu.ie3.util.osm.model.OsmEntity.Node
import tech.units.indriya.quantity.Quantities
import tech.units.indriya.unit.Units

import java.util.UUID

trait DefinitionsTestData {
  // test grid
  // ---- |  7  7.5  8  8.5  9
  // 52.0 |
  // 51.5 |      3       4
  // 51.0 |                  5
  // 50.5 |  1           6
  // 50.0 |  0       2

  // corresponding osm nodes
  protected val transitionPoint: Node = Node(
    id = 0L,
    latitude = 50.0,
    longitude = 7.0,
    tags = Map.empty,
    metaInformation = None
  )
  protected val osmNode1: Node = Node(
    id = 1L,
    latitude = 50.5,
    longitude = 7.0,
    tags = Map.empty,
    metaInformation = None
  )
  protected val osmNode2: Node = Node(
    id = 2L,
    latitude = 50.0,
    longitude = 8.0,
    tags = Map.empty,
    metaInformation = None
  )
  protected val osmNode3: Node = Node(
    id = 3L,
    latitude = 51.5,
    longitude = 7.5,
    tags = Map.empty,
    metaInformation = None
  )
  protected val osmNode4: Node = Node(
    id = 4L,
    latitude = 51.5,
    longitude = 8.5,
    tags = Map.empty,
    metaInformation = None
  )
  protected val osmNode5: Node = Node(
    id = 5L,
    latitude = 51.0,
    longitude = 9.0,
    tags = Map.empty,
    metaInformation = None
  )
  protected val osmNode6: Node = Node(
    id = 6L,
    latitude = 50.5,
    longitude = 8.5,
    tags = Map.empty,
    metaInformation = None
  )

  // corresponding PSDM nodes
  val nodeToHv = new NodeInput(
    UUID.randomUUID(),
    s"Transition point",
    Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
    true,
    GeoUtils.buildPoint(50.0, 7.0),
    GermanVoltageLevelUtils.MV_10KV,
    1
  )
  protected val nodeInMv1 = new NodeInput(
    UUID.randomUUID(),
    s"Node 1",
    Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
    false,
    GeoUtils.buildPoint(50.5, 7.0),
    GermanVoltageLevelUtils.MV_10KV,
    1
  )
  val nodeInMv2 = new NodeInput(
    UUID.randomUUID(),
    s"Node 2",
    Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
    false,
    GeoUtils.buildPoint(50.0, 8.0),
    GermanVoltageLevelUtils.MV_10KV,
    1
  )
  val nodeInMv3 = new NodeInput(
    UUID.randomUUID(),
    s"Node 3",
    Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
    false,
    GeoUtils.buildPoint(51.5, 7.5),
    GermanVoltageLevelUtils.MV_10KV,
    1
  )
  val nodeInMv4 = new NodeInput(
    UUID.randomUUID(),
    s"Node 4",
    Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
    false,
    GeoUtils.buildPoint(51.5, 8.5),
    GermanVoltageLevelUtils.MV_10KV,
    1
  )
  val nodeInMv5 = new NodeInput(
    UUID.randomUUID(),
    s"Node 5",
    Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
    false,
    GeoUtils.buildPoint(51.0, 9.0),
    GermanVoltageLevelUtils.MV_10KV,
    1
  )
  val nodeInMv6 = new NodeInput(
    UUID.randomUUID(),
    s"Node 6",
    Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
    false,
    GeoUtils.buildPoint(50.5, 8.5),
    GermanVoltageLevelUtils.MV_10KV,
    1
  )

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
    val uniqueConnections = Connections
      .getAllUniqueCombinations(
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

}
