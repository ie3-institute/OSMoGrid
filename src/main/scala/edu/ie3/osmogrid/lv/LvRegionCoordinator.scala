/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import akka.actor.typed.scaladsl.Behaviors

import akka.actor.typed.ActorRef

object LvRegionCoordinator {
  sealed trait Request

  def apply(
      lvGeneratorPool: ActorRef[LvGenerator.Request]
  ): Behaviors.Receive[Request] = idle(lvGeneratorPool)

  private def idle(
      lvGeneratorPool: ActorRef[LvGenerator.Request]
  ): Behaviors.Receive[Request] = Behaviors.receive { case (ctx, unsupported) =>
    ctx.log.warn(s"Received unsupported message '$unsupported'.")
    Behaviors.stopped
  }
}
