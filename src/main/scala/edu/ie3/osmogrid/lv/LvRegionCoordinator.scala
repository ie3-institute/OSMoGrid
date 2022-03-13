/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.Behaviors
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.osmogrid.cfg.OsmoGridConfig

object LvRegionCoordinator {
  sealed trait Request
  final case class Partition(
      lvConfig: OsmoGridConfig.Generation.Lv,
      replyTo: ActorRef[Response]
  ) extends Request // TODO: OSM data needs to be transferred

  sealed trait Response
  final case class RepLvGrids(subGrids: Seq[SubGridContainer]) extends Response

  def apply(): Behaviors.Receive[Request] = idle

  private def idle: Behaviors.Receive[Request] = Behaviors.receive {
    case (ctx, unsupported) =>
      ctx.log.warn(s"Received unsupported message '$unsupported'.")
      Behaviors.stopped
  }
}
