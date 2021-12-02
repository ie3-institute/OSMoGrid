/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorRef
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.guardian.OsmoGridGuardian.{
  OsmoGridGuardianEvent,
  RepLvGrids
}

object LvCoordinator {
  sealed trait LvCoordinatorEvent
  final case class ReqLvGrids(
      cfg: OsmoGridConfig.Generation.Lv,
      replyTo: ActorRef[OsmoGridGuardianEvent]
  ) extends LvCoordinatorEvent

  def apply(): Behavior[LvCoordinatorEvent] = idle

  private def idle: Behavior[LvCoordinatorEvent] = Behaviors.receive {
    (ctx, msg) =>
      msg match {
        case ReqLvGrids(cfg, replyTo) =>
          ctx.log.info("Starting generation of low voltage grids!")
          /* TODO: Split up osm data at municipality boundaries, spawn needed actors and start generation */
          replyTo ! RepLvGrids(Vector.empty[SubGridContainer])
          Behaviors.stopped
        case unsupported =>
          ctx.log.error(s"Received unsupported message: $unsupported")
          Behaviors.stopped
      }
  }
}
