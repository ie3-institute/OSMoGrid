/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package utils

import edu.ie3.datamodel.models.voltagelevels.VoltageLevel
import tech.units.indriya.quantity.Quantities
import tech.units.indriya.unit.Units

object VoltageLevelUtils {
  def getLvVoltLvl(voltageLevel: List[String]): List[VoltageLevel] = {
    voltageLevel.map(str => {
      new VoltageLevel(
        "lv",
        Quantities.getQuantity(str.toDouble * 1000, Units.VOLT)
      )
    })
  }

  def getMvVoltLvl(voltageLevel: List[String]): List[VoltageLevel] = {
    voltageLevel.map(str => {
      new VoltageLevel(
        "mv",
        Quantities.getQuantity(str.toDouble * 1000, Units.VOLT)
      )
    })

  }
}
