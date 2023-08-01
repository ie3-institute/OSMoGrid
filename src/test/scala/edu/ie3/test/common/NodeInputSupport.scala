/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.test.common

import edu.ie3.datamodel.models.StandardUnits
import edu.ie3.datamodel.models.input.NodeInput
import edu.ie3.datamodel.models.voltagelevels.GermanVoltageLevelUtils
import edu.ie3.util.geo.GeoUtils
import tech.units.indriya.quantity.Quantities

import java.util.UUID

trait NodeInputSupport {
  protected val nodeA = new NodeInput(
    UUID.randomUUID(),
    s"Node A in 1",
    Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
    false,
    GeoUtils.buildPoint(51.49249, 7.41105),
    GermanVoltageLevelUtils.MV_10KV,
    1
  )
  val nodeB = new NodeInput(
    UUID.randomUUID(),
    s"Node B in 1",
    Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
    false,
    GeoUtils.buildPoint(51.49276, 7.41657),
    GermanVoltageLevelUtils.MV_10KV,
    1
  )
  val nodeC = new NodeInput(
    UUID.randomUUID(),
    s"Node C in 2",
    Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
    false,
    GeoUtils.buildPoint(51.49350, 7.41605),
    GermanVoltageLevelUtils.MV_10KV,
    2
  )

  val nodeD = new NodeInput(
    UUID.randomUUID(),
    s"Node C in 2",
    Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
    false,
    GeoUtils.buildPoint(51.49360, 7.41835),
    GermanVoltageLevelUtils.MV_10KV,
    2
  )

  val nodeToHv1 = new NodeInput(
    UUID.randomUUID(),
    s"Node 1 in 1",
    Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
    false,
    GeoUtils.buildPoint(51.48340, 7.31635),
    GermanVoltageLevelUtils.MV_10KV,
    1
  )

  val nodeToHv2 = new NodeInput(
    UUID.randomUUID(),
    s"Node 2 in 2",
    Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
    false,
    GeoUtils.buildPoint(51.49570, 7.42505),
    GermanVoltageLevelUtils.MV_10KV,
    2
  )

  val nodeToHv3 = new NodeInput(
    UUID.randomUUID(),
    s"Node 3 in 2",
    Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
    false,
    GeoUtils.buildPoint(51.45570, 7.47505),
    GermanVoltageLevelUtils.MV_10KV,
    2
  )
}
