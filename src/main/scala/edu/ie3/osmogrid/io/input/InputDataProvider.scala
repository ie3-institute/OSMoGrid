/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.io.input

import akka.actor.typed.Behavior

object InputDataProvider {

  sealed trait InputDataEvent

  final case class Read()
      extends InputDataEvent // todo this read method should contain configuration parameters for the actual source + potential filter options

  def apply(): Behavior[InputDataEvent] = ???

}
