/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import akka.actor.typed.scaladsl.Behaviors

import akka.actor.typed.ActorRef

object LvRegionCoordinator {
  sealed trait Request

  def apply(
      lvGeneratorPool: ActorRef[LvGridGenerator.Request]
  ): Behaviors.Receive[Request] = ???
}
