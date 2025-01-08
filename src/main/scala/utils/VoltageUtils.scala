/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */
package utils

import edu.ie3.datamodel.models.StandardUnits
import edu.ie3.datamodel.models.voltagelevels.VoltageLevel
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import tech.units.indriya.ComparableQuantity
import tech.units.indriya.quantity.Quantities

import javax.measure.Quantity
import javax.measure.quantity.ElectricPotential

object VoltageUtils {

  /** Method to parse a [[edu.ie3.osmogrid.cfg.OsmoGridConfig.Voltage.Lv]]
    * easily.
    *
    * @param cfg
    *   given config
    * @return
    *   a list of [[Quantity]]
    */
  def parse(
      cfg: OsmoGridConfig.Voltage.Lv
  ): List[ComparableQuantity[ElectricPotential]] = {
    toQuantities(cfg.vNom, cfg.default)
  }

  /** Method to parse a [[edu.ie3.osmogrid.cfg.OsmoGridConfig.Voltage.Mv]]
    * easily.
    *
    * @param cfg
    *   given config
    * @return
    *   a list of [[Quantities]]
    */
  def parse(
      cfg: OsmoGridConfig.Voltage.Mv
  ): List[ComparableQuantity[ElectricPotential]] = {
    toQuantities(cfg.vNom, cfg.default)
  }

  /** Method to parse a [[edu.ie3.osmogrid.cfg.OsmoGridConfig.Voltage.Hv]]
    * easily.
    *
    * @param cfg
    *   given config
    * @return
    *   a list of [[Quantities]]
    */
  def parse(
      cfg: OsmoGridConfig.Voltage.Hv
  ): List[ComparableQuantity[ElectricPotential]] = {
    toQuantities(cfg.vNom, cfg.default)
  }

  /** Utility to create a list of [[Quantity]].
    * @param id
    *   of the voltage level
    * @param vNom
    *   option for multiple voltages in kV
    * @param default
    *   a default voltage that should be used, if vNom is an empty option
    * @return
    *   a list of [[VoltageLevel]]s
    */
  def toVoltLvl(
      id: String,
      vNom: Option[List[Double]],
      default: Double
  ): List[VoltageLevel] = {
    toQuantities(vNom, default).map { quantity =>
      new VoltageLevel(id, quantity)
    }
  }

  /** Utility to create a list of [[Quantity]].
    *
    * @param vNom
    *   option for multiple voltages in kV
    * @param default
    *   a default voltage that should be used, if vNom is an empty option
    * @return
    *   a list of [[ComparableQuantity]] with the unit [[ElectricPotential]]
    */
  def toQuantities(
      vNom: Option[List[Double]],
      default: Double
  ): List[ComparableQuantity[ElectricPotential]] = {
    vNom match {
      case Some(voltages) =>
        if (voltages.isEmpty) {
          List(toQuantity(default))
        } else {
          voltages.map(voltage => toQuantity(voltage))
        }
      case None => List(toQuantity(default))
    }
  }

  /** Converts a [[Double]] value in a [[ComparableQuantity]] with the unit
    * [[ElectricPotential]].
    * @param volt
    *   given voltage in kV
    * @return
    *   a new [[ComparableQuantity]]
    */
  def toQuantity(volt: Double): ComparableQuantity[ElectricPotential] =
    Quantities.getQuantity(volt, StandardUnits.RATED_VOLTAGE_MAGNITUDE)
}
