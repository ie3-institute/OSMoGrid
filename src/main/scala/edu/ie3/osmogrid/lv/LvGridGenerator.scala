/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import akka.actor.typed.scaladsl.Behaviors

object LvGridGenerator {
  sealed trait Request

  sealed trait Response

  def apply(): Behaviors.Receive[Request] = idle

  private def idle: Behaviors.Receive[Request] = Behaviors.receive {
    case (ctx, unsupported) =>
      ctx.log.warn(s"Received unsupported message '$unsupported'.")
      Behaviors.stopped
  }
}
