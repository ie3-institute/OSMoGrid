/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package utils

import edu.ie3.datamodel.models.voltagelevels.VoltageLevel
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import tech.units.indriya.ComparableQuantity
import tech.units.indriya.quantity.Quantities
import tech.units.indriya.unit.Units

import javax.measure.quantity.ElectricPotential

object VoltageLevelUtils {

  /** Converts a [[OsmoGridConfig.VoltageLevelConfig.VoltageLevel]] into a list
    * of [[VoltageLevel]]. [[VoltageLevel]].
    * @param cfg
    *   config
    * @return
    *   list of [[VoltageLevel]]
    */
  def getVoltLvl(
      cfg: OsmoGridConfig.VoltageLevelConfig.VoltageLevel
  ): List[VoltageLevel] = {
    val default = cfg.default

    cfg.vNom match {
      case Some(values) =>
        values.map(value => getVoltLvl(cfg.id, toQuantity(value)))
      case None => List(getVoltLvl(cfg.id, toQuantity(default)))
    }
  }

  /** Method to convert a given double into a [[VoltageLevel]].
    * @param id
    *   can be "lv", "mv", "hv" or "ehv"
    * @param volt
    *   electric potential in volt
    * @return
    *   a new [[VoltageLevel]]
    */
  def getVoltLvl(
      id: String,
      volt: ComparableQuantity[ElectricPotential]
  ): VoltageLevel = {
    new VoltageLevel(id, volt)
  }

  /** Converts a double into a quantity.
    * @param volt
    *   given double
    * @return
    *   a new [[ComparableQuantity]]
    */
  private def toQuantity(
      volt: Double
  ): ComparableQuantity[ElectricPotential] = {
    Quantities.getQuantity(volt * 1000, Units.VOLT)
  }
}
