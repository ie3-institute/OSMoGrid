/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import akka.actor.typed.scaladsl.Behaviors

import akka.actor.typed.ActorRef
import edu.ie3.osmogrid.lv.LvGenerator.LvGeneratorEvent

object LvRegionCoordinator {
  sealed trait LvRegionCoordinatorEvent

  def apply(
      lvGeneratorPool: ActorRef[LvGeneratorEvent]
  ): Behaviors.Receive[LvRegionCoordinatorEvent] = idle(lvGeneratorPool)

  private def idle(
      lvGeneratorPool: ActorRef[LvGeneratorEvent]
  ): Behaviors.Receive[LvRegionCoordinatorEvent] = Behaviors.receive {
    case (ctx, unsupported) =>
      ctx.log.warn(s"Received unsupported message '$unsupported'.")
      Behaviors.stopped
  }
}
