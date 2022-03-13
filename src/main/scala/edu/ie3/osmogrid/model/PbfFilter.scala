/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.model

sealed trait PbfFilter

object PbfFilter {
  object DummyFilter extends PbfFilter
}
