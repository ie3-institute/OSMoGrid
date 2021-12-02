/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.guardian

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.cfg.OsmoGridConfig.Generation
import edu.ie3.osmogrid.io.input.InputDataProvider
import edu.ie3.osmogrid.io.output.ResultListener
import edu.ie3.osmogrid.lv.LvCoordinator
import edu.ie3.osmogrid.lv.LvCoordinator.ReqLvGrids

object OsmoGridGuardian {

  sealed trait OsmoGridGuardianEvent
  final case class Run(cfg: OsmoGridConfig) extends OsmoGridGuardianEvent
  object InputDataProviderDied extends OsmoGridGuardianEvent
  object ResultEventListenerDied extends OsmoGridGuardianEvent
  final case class RepLvGrids(grids: Vector[SubGridContainer])
      extends OsmoGridGuardianEvent

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

          /* Check, which voltage level configs are given. Start with lv level, if this is desired for. */
          cfg.generation match {
            case Generation(Some(lvConfig)) =>
              ctx.log.debug("Starting low voltage grid coordinator.")
              val lvCoordinator = ctx.spawn(LvCoordinator(), "LvCoordinator")
              lvCoordinator ! ReqLvGrids(lvConfig, ctx.self)
              awaitLvGrids
            case unsupported =>
              ctx.log.error(
                "Received unsupported grid generation config. Bye, bye."
              )
              Behaviors.stopped
          }
        case InputDataProviderDied =>
          ctx.log.error("Input data provider died. That's bad...")
          Behaviors.stopped
        case ResultEventListenerDied =>
          ctx.log.error("Result event listener died. That's bad...")
          Behaviors.stopped
      }
    }

  private def awaitLvGrids: Behaviors.Receive[OsmoGridGuardianEvent] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case RepLvGrids(lvGrids) => ???
        /* TODO: Build JointGridContainer and hand over to ResultEventListener */
        case unsupported =>
          ctx.log.error(
            s"Received unsupported message while waiting for lv grids. Unsupported: $unsupported"
          )
          Behaviors.stopped
      }
    }
}
