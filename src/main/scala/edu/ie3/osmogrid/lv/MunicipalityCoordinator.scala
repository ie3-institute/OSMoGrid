/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

object MunicipalityCoordinator {
  sealed trait Request

  sealed trait Response

  def apply(
      districtCoordinator: ActorRef[DistrictCoordinator.Request]
  ): Behavior[Request] = idle(districtCoordinator)

  def idle(
      districtCoordinator: ActorRef[DistrictCoordinator.Request]
  ): Behavior[Request] = Behaviors.receive { (ctx, msg) =>
    ctx.log.info(s"Received a message: $msg")
    Behaviors.same
  }
}