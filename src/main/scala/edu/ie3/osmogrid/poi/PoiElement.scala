/*
 * © 2026. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.poi

import java.util

final case class PoiElement(
    id: Long,
    poiType: String,
    lat: Double,
    lon: Double,
    size: Double,
)
