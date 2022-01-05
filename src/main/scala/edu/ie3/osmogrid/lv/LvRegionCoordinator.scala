/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.Behaviors

object LvRegionCoordinator {
  sealed trait Request
  final case class Partition(replyTo: ActorRef[Response])
      extends Request // TODO: OSM data needs to be transferred

  sealed trait Response
  final case class Done(amountOfGrids: Int)
      extends Response // TODO: Needs to contain reference to the region!

  def apply(
      municipalityCoordinator: ActorRef[MunicipalityCoordinator.Request]
  ): Behaviors.Receive[Request] = idle(municipalityCoordinator)

  private def idle(
      municipalityCoordinator: ActorRef[MunicipalityCoordinator.Request]
  ): Behaviors.Receive[Request] = Behaviors.receive { case (ctx, unsupported) =>
    ctx.log.warn(s"Received unsupported message '$unsupported'.")
    Behaviors.stopped
  }
}
