/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.cfg

import edu.ie3.osmogrid.cfg.OsmoGridConfig.Generation.Lv
import edu.ie3.osmogrid.cfg.OsmoGridConfig.Generation.Lv.BoundaryAdminLevel
import edu.ie3.osmogrid.cfg.OsmoGridConfig.Generation.Lv.Osm

object ConfigFallback {
  val lvConfig: Lv = Lv(
    averagePowerDensity = 2000,
    boundaryAdminLevel = BoundaryAdminLevel(lowest = 8, starting = 2),
    considerHouseConnectionPoints = false,
    loadSimultaneousFactor = 0.2,
    minDistance = 10,
    osm = Osm(None)
  )

}
