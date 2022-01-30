/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.Behaviors
import edu.ie3.util.osm.model.OsmContainer

object MunicipalityCoordinator {
  sealed trait Request

  def apply(
      osmContainer: OsmContainer
  ): Behaviors.Receive[Request] = ???
}
