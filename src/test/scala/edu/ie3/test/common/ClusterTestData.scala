/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.test.common

import edu.ie3.datamodel.models.input.NodeInput
import edu.ie3.datamodel.models.input.connector.LineInput
import edu.ie3.datamodel.models.input.system.LoadInput
import edu.ie3.datamodel.models.voltagelevels.{
  CommonVoltageLevel,
  GermanVoltageLevelUtils,
}
import edu.ie3.osmogrid.lv.LvGridGeneratorSupport.GridElements
import edu.ie3.util.geo.GeoUtils
import edu.ie3.util.geo.GeoUtils.calcHaversine
import edu.ie3.util.osm.model.OsmEntity.Node
import edu.ie3.util.quantities.QuantityUtils.RichQuantityDouble
import utils.GridConversion.{buildLine, buildLoad}

import java.util.UUID

trait ClusterTestData extends GridSupport {
  protected val voltageLevel: CommonVoltageLevel = GermanVoltageLevelUtils.LV
  protected def lineBuilder(nodeA: NodeInput, nodeB: NodeInput): LineInput =
    buildLine(
      s"${nodeA.getId} -> ${nodeB.getId}",
      nodeA,
      nodeB,
      1,
      defaultLineTypeLv,
      calcHaversine(
        nodeA.getGeoPosition.getCoordinate,
        nodeB.getGeoPosition.getCoordinate,
      ),
    )

  // test points
  protected val (osm1_1, p1_1) = buildPoint(11L, 50d, 7d)
  protected val (osm1_2, p1_2) = buildPoint(12L, 50d, 7.003)
  protected val (osm1_3, p1_3) = buildPoint(13L, 49.997, 7d)
  protected val (osm1_4, p1_4) = buildPoint(14L, 49.997, 7.003)

  protected val (osm2_1, p2_1) = buildPoint(21L, 50.25, 7.1)
  protected val (osm2_2, p2_2) = buildPoint(22L, 50.2, 7.103)
  protected val (osm2_3, p2_3) = buildPoint(23L, 50.303, 7.1)
  protected val (osm2_4, p2_4) = buildPoint(24L, 50.302, 7.103)

  protected val nodeMap: Map[Node, NodeInput] = List(
    osm1_1 -> p1_1,
    osm1_2 -> p1_2,
    osm1_3 -> p1_3,
    osm1_4 -> p1_4,
    osm2_1 -> p2_1,
    osm2_2 -> p2_2,
    osm2_3 -> p2_3,
    osm2_4 -> p2_4,
  ).toMap

  protected val l1_1: LoadInput =
    buildLoad("load for node 1_1", 200.asKiloVoltAmpere)(p1_1)
  protected val l1_2: LoadInput =
    buildLoad("load for node 1_2", 200.asKiloVoltAmpere)(p1_2)
  protected val l1_3: LoadInput =
    buildLoad("load for node 1_3", 200.asKiloVoltAmpere)(p1_3)
  protected val l1_4: LoadInput =
    buildLoad("load for node 1_4", 100.asKiloVoltAmpere)(p1_4)
  protected val l2_1: LoadInput =
    buildLoad("load for node 2_1", 200.asKiloVoltAmpere)(p2_1)
  protected val l2_2: LoadInput =
    buildLoad("load for node 2_2", 100.asKiloVoltAmpere)(p2_2)
  protected val l2_3: LoadInput =
    buildLoad("load for node 2_3", 200.asKiloVoltAmpere)(p2_3)
  protected val l2_4: LoadInput =
    buildLoad("load for node 2_4", 100.asKiloVoltAmpere)(p2_4)

  protected val lines: Set[LineInput] = List(
    lineBuilder(p1_1, p1_2),
    lineBuilder(p1_2, p1_3),
    lineBuilder(p1_2, p1_4),
    lineBuilder(p2_1, p1_1),
    lineBuilder(p2_2, p1_2),
    lineBuilder(p2_1, p2_2),
    lineBuilder(p2_2, p2_3),
    lineBuilder(p2_3, p2_4),
  ).toSet

  protected def gridElements(substations: List[NodeInput]): GridElements =
    GridElements(
      nodeMap.filter { case (_, input) => !substations.contains(input) },
      nodeMap.filter { case (_, input) => substations.contains(input) },
      Set(l1_1, l1_2, l1_3, l1_4, l2_1, l2_2, l2_3, l2_4),
    )

  def buildPoint(i: Long, lat: Double, lon: Double): (Node, NodeInput) = {
    val node = Node(i, lat, lon, Map.empty, None)
    val nodeInput = new NodeInput(
      UUID.randomUUID(),
      s"Node $i",
      1.asPu,
      false,
      GeoUtils.buildPoint(lat, lon),
      GermanVoltageLevelUtils.LV,
      1,
    )
    (node, nodeInput)
  }
}
