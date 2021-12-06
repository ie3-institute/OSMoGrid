/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.io.input

import akka.actor.typed.Behavior
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.Behaviors
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.guardian.OsmoGridGuardian.OsmoGridGuardianEvent

object InputDataProvider {

  sealed trait InputDataEvent
  final case class Read()
      extends InputDataEvent // todo this read method should contain configuration parameters for the actual source + potential filter options
  final case class Terminate(replyTo: ActorRef[OsmoGridGuardianEvent])
      extends InputDataEvent

  def apply(cfg: OsmoGridConfig.Input): Behavior[InputDataEvent] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case _: Read =>
          ctx.log.warn("Reading of data not yet implemented.")
          Behaviors.same
        case Terminate(_) =>
          ctx.log.info("Stopping input data provider")
          // TODO: Any closing of sources and stuff
          Behaviors.stopped
      }
    }

}
