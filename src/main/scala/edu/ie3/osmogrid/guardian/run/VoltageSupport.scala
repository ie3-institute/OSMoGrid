/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.guardian.run

import edu.ie3.osmogrid.cfg.OsmoGridConfig.{Voltage, VoltageLevel}

/** Simple wrapper object that holds a [[Voltage]] config for global access. The
  * current config can be retrieved via the [[getVoltageConfig]] method.
  */
trait VoltageSupport {

  private var cfg: Option[Voltage] = None

  /** Setter for new config values.
    * @param config
    *   that should replace the old one
    */
  private[run] def setVoltageConfig(config: Voltage): Unit = {
    cfg = Some(config)
  }

  /** Returns the [[Voltage]] config or a default value.
    */
  def getVoltageConfig: Voltage = cfg.getOrElse(Voltage())
}
