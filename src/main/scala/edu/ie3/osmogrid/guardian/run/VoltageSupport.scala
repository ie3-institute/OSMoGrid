/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.guardian.run

import edu.ie3.osmogrid.cfg.OsmoGridConfig.Voltage
import edu.ie3.osmogrid.cfg.OsmoGridConfig.Voltage.{Hv, Lv, Mv}

/** Simple wrapper object that holds a [[Voltage]] config for global access. The
  * current config can be retrieved via the [[get]] method.
  */
trait VoltageSupport {

  /** Default [[Voltage]] values.
    */
  val DEFAULT: Voltage = Voltage(
    Hv(110.0, "hv", None),
    Lv(0.4, "lv", None),
    Mv(10.0, "mv", None)
  )

  private var cfg: Option[Voltage] = None

  /** Setter for new config values.
    * @param config
    *   that should replace the old one
    */
  private[run] def set(config: Voltage): Unit = {
    cfg = Some(config)
  }

  /** Returns the [[Voltage]] config or a default value.
    */
  def get: Voltage = cfg.getOrElse(DEFAULT)
}
