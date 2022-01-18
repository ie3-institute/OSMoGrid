/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.io.input

import akka.actor.typed.Behavior
import akka.actor.typed.ActorRef
import akka.actor.typed.PostStop
import akka.actor.typed.scaladsl.Behaviors
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.guardian.OsmoGridGuardian

object InputDataProvider {

  sealed trait Request
  final case class Read()
      extends Request // todo this read method should contain configuration parameters for the actual source + potential filter options
  final case class Terminate(replyTo: ActorRef[OsmoGridGuardian.Request])
      extends Request

  sealed trait Response

  def apply(cfg: OsmoGridConfig.Input): Behavior[Request] =
    Behaviors
      .receive[Request] {
        case (ctx, _: Read) =>
          ctx.log.warn("Reading of data not yet implemented.")
          Behaviors.same
        case (ctx, Terminate(_)) =>
          ctx.log.info("Stopping input data provider")
          // TODO: Any closing of sources and stuff
          Behaviors.stopped
      }
      .receiveSignal { case (ctx, PostStop) =>
        ctx.log.info("Requested to stop.")
        // TODO: Any closing of sources and stuff
        Behaviors.same
      }
}
