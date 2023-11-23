/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

object SubDistrictCoordinator {
  def apply(): Behavior[SubDistrictRequest] = idle

  def idle: Behavior[SubDistrictRequest] = Behaviors.receive { (ctx, msg) =>
    ctx.log.info(s"Received a message: $msg")
    Behaviors.same
  }
}
