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
  protected val nodeInMv1 = new NodeInput(
    UUID.randomUUID(),
    s"Node 1 in mv",
    Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
    false,
    GeoUtils.buildPoint(51.49, 7.49),
    GermanVoltageLevelUtils.MV_10KV,
    1
  )
  val nodeInMv2 = new NodeInput(
    UUID.randomUUID(),
    s"Node 2 in mv",
    Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
    false,
    GeoUtils.buildPoint(51.51, 7.49),
    GermanVoltageLevelUtils.MV_10KV,
    1
  )
  val nodeInMv3 = new NodeInput(
    UUID.randomUUID(),
    s"Node 3 in mv",
    Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
    false,
    GeoUtils.buildPoint(51.49, 7.51),
    GermanVoltageLevelUtils.MV_10KV,
    1
  )

  val nodeInMv4 = new NodeInput(
    UUID.randomUUID(),
    s"Node 4 in mv",
    Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
    false,
    GeoUtils.buildPoint(51.51, 7.51),
    GermanVoltageLevelUtils.MV_10KV,
    1
  )

  val nodeToHv1 = new NodeInput(
    UUID.randomUUID(),
    s"Node 1 to hv",
    Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
    false,
    GeoUtils.buildPoint(51.0, 7.0),
    GermanVoltageLevelUtils.MV_10KV,
    1
  )

  val nodeToHv2 = new NodeInput(
    UUID.randomUUID(),
    s"Node 2 ti hv",
    Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
    false,
    GeoUtils.buildPoint(52.0, 7.0),
    GermanVoltageLevelUtils.MV_10KV,
    1
  )

  val nodeToHv3 = new NodeInput(
    UUID.randomUUID(),
    s"Node 3 to hv",
    Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
    false,
    GeoUtils.buildPoint(51.0, 8.0),
    GermanVoltageLevelUtils.MV_10KV,
    1
  )

  val nodeToHv4 = new NodeInput(
    UUID.randomUUID(),
    s"Node 4 to hv",
    Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
    false,
    GeoUtils.buildPoint(52.0, 8.0),
    GermanVoltageLevelUtils.MV_10KV,
    1
  )
}
