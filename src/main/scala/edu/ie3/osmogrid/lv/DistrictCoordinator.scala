/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

object DistrictCoordinator {
  sealed trait Request

  sealed trait Response

  def apply(
      subDistrictCoordinator: ActorRef[SubDistrictCoordinator.Request]
  ): Behavior[Request] = idle(subDistrictCoordinator)

  def idle(
      subDistrictCoordinator: ActorRef[SubDistrictCoordinator.Request]
  ): Behavior[Request] = Behaviors.receive { (ctx, msg) =>
    ctx.log.info(s"Received a message: $msg")
    Behaviors.same
  }
}