/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import akka.actor.typed.scaladsl.Behaviors

object LvGenerator {
  sealed trait LvGeneratorEvent

  def apply(): Behaviors.Receive[LvGeneratorEvent] = idle

  private def idle: Behaviors.Receive[LvGeneratorEvent] = Behaviors.receive {
    case (ctx, unsupported) =>
      ctx.log.warn(s"Received unsupported message '$unsupported'.")
      Behaviors.stopped
  }
}
