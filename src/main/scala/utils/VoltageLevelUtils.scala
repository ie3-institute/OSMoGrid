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

  /** Method to parse a [[OsmoGridConfig.VoltageLevelConfig.Lv]] easily.
    * @param cfg
    *   given config
    * @return
    *   a list of [[VoltageLevel]]
    */
  def parseLv(cfg: OsmoGridConfig.VoltageLevelConfig.Lv): List[VoltageLevel] = {
    getVoltLvl(cfg.id, cfg.vNom, cfg.default)
  }

  /** Method to parse a [[OsmoGridConfig.VoltageLevelConfig.Mv]] easily.
    *
    * @param cfg
    *   given config
    * @return
    *   a list of [[VoltageLevel]]
    */
  def parseMv(cfg: OsmoGridConfig.VoltageLevelConfig.Mv): List[VoltageLevel] = {
    getVoltLvl(cfg.id, cfg.vNom, cfg.default)
  }

  /** Method to parse a [[OsmoGridConfig.VoltageLevelConfig.Hv]] easily.
    *
    * @param cfg
    *   given config
    * @return
    *   a list of [[VoltageLevel]]
    */
  def parseHv(cfg: OsmoGridConfig.VoltageLevelConfig.Hv): List[VoltageLevel] = {
    getVoltLvl(cfg.id, cfg.vNom, cfg.default)
  }

  /** Method to parse a [[OsmoGridConfig.VoltageLevelConfig.Ehv]] easily.
    *
    * @param cfg
    *   given config
    * @return
    *   a list of [[VoltageLevel]]
    */
  def parseEhv(
      cfg: OsmoGridConfig.VoltageLevelConfig.Ehv
  ): List[VoltageLevel] = {
    getVoltLvl(cfg.id, cfg.vNom, cfg.default)
  }

  /** Converts the given values into a list of [[VoltageLevel]].
    * @param id
    *   of the voltage level
    * @param vNom
    *   option for nominal voltages
    * @param default
    *   voltage that is used if no nominal voltages are given
    * @return
    *   a list of [[VoltageLevel]]
    */
  def getVoltLvl(
      id: String,
      vNom: Option[List[Double]],
      default: Double
  ): List[VoltageLevel] = {
    vNom match {
      case Some(voltages) =>
        voltages.map(voltage => new VoltageLevel(id, toQuantity(voltage)))
      case None => List(new VoltageLevel(id, toQuantity(default)))
    }
  }

  /** Converts a [[Double]] value in a [[ComparableQuantity]] with the unit
    * [[ElectricPotential]].
    * @param volt
    *   given voltage in kV
    * @return
    *   a new [[ComparableQuantity]]
    */
  private def toQuantity(volt: Double): ComparableQuantity[ElectricPotential] =
    Quantities.getQuantity(volt * 1000, Units.VOLT)
}
