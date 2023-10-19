/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.cfg

import edu.ie3.osmogrid.cfg.OsmoGridConfig.Voltage
import edu.ie3.osmogrid.cfg.OsmoGridConfig.Voltage.{Hv, Lv, Mv}

case object VoltageConfigWrapper {
  var cfg: Voltage = Voltage(
    Hv(110.0, "hv", None),
    Lv(0.4, "lv", None),
    Mv(10.0, "mv", None)
  )
}
