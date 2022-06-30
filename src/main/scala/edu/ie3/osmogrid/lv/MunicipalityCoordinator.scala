/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import akka.actor.typed.scaladsl.Behaviors
import edu.ie3.osmogrid.model.OsmoGridModel

object MunicipalityCoordinator {
  sealed trait Request

  def apply(
      osmoGridModel: OsmoGridModel
  ): Behaviors.Receive[Request] = idle()

  def idle(): Behaviors.Receive[Request] = Behaviors.receive { (ctx, msg) =>
    ctx.log.info(s"Received a message: $msg")
    Behaviors.same
  }
}
