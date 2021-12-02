/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.guardian

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import edu.ie3.osmogrid.cfg.OsmoGridConfig

object OsmoGridGuardian {

  sealed trait OsmoGridGuardianEvent
  final case class Run(cfg: OsmoGridConfig) extends OsmoGridGuardianEvent

  def apply(): Behavior[OsmoGridGuardianEvent] = idle()

  private def idle(): Behavior[OsmoGridGuardianEvent] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case Run(cfg) =>
          ctx.log.info("Initializing grid generation!")
          /*
           * TODO:
           *  1) Spawn InputDataProvider and initialize it
           *  2) Spawn result listener and initialize it
           *  3) Await both completion messages
           */
          Behaviors.stopped
      }
    }
}
