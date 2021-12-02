/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.guardian

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.io.input.InputDataProvider
import edu.ie3.osmogrid.io.output.ResultListener

object OsmoGridGuardian {

  sealed trait OsmoGridGuardianEvent
  final case class Run(cfg: OsmoGridConfig) extends OsmoGridGuardianEvent
  object InputDataProviderDied extends OsmoGridGuardianEvent
  object ResultEventListenerDied extends OsmoGridGuardianEvent

  def apply(): Behavior[OsmoGridGuardianEvent] = idle()

  private def idle(): Behavior[OsmoGridGuardianEvent] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case Run(cfg) =>
          ctx.log.info("Initializing grid generation!")

          ctx.log.info("Starting input data provider")
          val inputProvider =
            ctx.spawn(InputDataProvider(cfg.input), "InputDataProvider")
          ctx.watchWith(inputProvider, InputDataProviderDied)
          ctx.log.debug("Starting output data listener")
          val resultEventListenerDied =
            ctx.spawn(ResultListener(cfg.output), "ResultListener")
          ctx.watchWith(resultEventListenerDied, ResultEventListenerDied)
          /*
           * TODO: Spawn LvCoordinator and trigger it for action
           */
          Behaviors.stopped
        case InputDataProviderDied =>
          ctx.log.error("Input data provider died. That's bad...")
          Behaviors.stopped
        case ResultEventListenerDied =>
          ctx.log.error("Result event listener died. That's bad...")
          Behaviors.stopped
      }
    }
}
