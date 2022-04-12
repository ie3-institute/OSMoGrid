/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv.region_coordinator

/** Possible future enhancement: Strategy that cuts up composed entities by
  * means of intersection with boundary polygon
  */
enum EntityAllocationStrategy {

  /** If at least one entity lies within the boundary
    */
  case AssignToAll

  /** Assigns entity to area with the maximum matched sub entities
    */
  case AssignByMax
}
