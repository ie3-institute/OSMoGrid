/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.model

import edu.ie3.util.osm.OsmEntities.{OsmEntity, Way}
import tech.units.indriya.ComparableQuantity

import javax.measure.quantity.Power

/** Point-based location of an electrical node
  *
  * @param location
  *   The geographical location
  * @param load
  *   Electrical load
  * @param source
  *   Source osm entity, the load has been derived from
  * @tparam T
  *   Type of source
  */
final case class LoadLocation[T <: OsmEntity](
    location: Coordinate,
    load: ComparableQuantity[Power],
    source: T
)
