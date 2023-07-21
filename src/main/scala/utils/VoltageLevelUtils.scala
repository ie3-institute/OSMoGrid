/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package utils

import edu.ie3.datamodel.models.voltagelevels.VoltageLevel
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import tech.units.indriya.quantity.Quantities
import tech.units.indriya.unit.Units

object VoltageLevelUtils {

  /** Converts [[OsmoGridConfig.VoltageLevelConfig]] into a list of
    * [[VoltageLevel]].
    * @param id
    *   of the voltage level
    * @param cfg
    *   config
    * @return
    *   a list of [[VoltageLevel]]
    */
  def getVoltLvl(
      id: String,
      cfg: OsmoGridConfig.VoltageLevelConfig
  ): List[VoltageLevel] = {
    val vNom: Option[List[Double]] = id match {
      case "lv"  => cfg.lv.map(c => c.vNom)
      case "mv"  => cfg.mv.map(c => c.vNom)
      case "hv"  => cfg.hv.map(c => c.vNom)
      case "ehv" => cfg.ehv.map(c => c.vNom)
      case _ =>
        throw new IllegalArgumentException(
          s"Argument $id is not a valid argument. Valid arguments are {lv, mv, hv, ehv}."
        )
    }

    vNom match {
      case Some(values) => values.map(value => getVoltLvl(id, value))
      case None         => getDefaultVoltLvl(id)
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
      volt: Double
  ): VoltageLevel = {
    new VoltageLevel(id, Quantities.getQuantity(volt * 1000, Units.VOLT))
  }

  /** Method to get the default voltage level for a given id.
    *
    * @param id
    *   can be "lv", "mv", "hv" or "ehv"
    * @return
    *   a new [[VoltageLevel]]
    */
  private def getDefaultVoltLvl(id: String): List[VoltageLevel] =
    id match {
      case "lv"  => List(getVoltLvl("lv", 0.4))
      case "mv"  => List(getVoltLvl("mv", 10.0))
      case "hv"  => List(getVoltLvl("hv", 110.0))
      case "ehv" => List(getVoltLvl("ehv", 380.0))
      case _ =>
        throw new IllegalArgumentException(
          s"Argument $id is not a valid argument. Valid arguments are {lv, mv, hv, ehv}."
        )
    }
}
