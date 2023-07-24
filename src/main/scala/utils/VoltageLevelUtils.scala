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

  /** Method to parse a [[OsmoGridConfig.Generation.Mv.VoltageLevel]] easily.
    *
    * @param cfg
    *   given config
    * @return
    *   a list of [[VoltageLevel]]
    */
  def parseMv(
      cfg: OsmoGridConfig.Generation.Mv.VoltageLevel
  ): List[VoltageLevel] = {
    toVoltLvl(cfg.id, cfg.vNom, cfg.default)
  }

  /** Utility to create a list of [[VoltageLevel]].
    * @param id
    *   of the voltage level
    * @param vNom
    *   option for multiple voltages in kV
    * @param default
    *   a default voltage that should be used, if vNom is an empty option
    * @return
    */
  def toVoltLvl(
      id: String,
      vNom: Option[List[Double]],
      default: Double
  ): List[VoltageLevel] = {
    vNom match {
      case Some(voltages) => voltages.map(voltage => toVoltLvl(id, voltage))
      case None           => List(toVoltLvl(id, default))
    }
  }

  /** Utility for creating a [[VoltageLevel]].
    * @param id
    *   of the voltage level
    * @param lvl
    *   value given in kV
    * @return
    *   a new [[VoltageLevel]]
    */
  def toVoltLvl(id: String, lvl: Double): VoltageLevel = {
    new VoltageLevel(id, toQuantity(lvl))
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
