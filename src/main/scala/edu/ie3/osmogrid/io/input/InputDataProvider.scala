/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.io.input

import akka.actor.typed.{ActorRef, Behavior}
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.util.osm.OsmModel

object InputDataProvider {

  sealed trait InputDataEvent
  final case class Init(cfg: OsmoGridConfig, replyTo: ActorRef[Response]) extends InputDataEvent
  final case class ReqOsm(importPath: String, replyTo: ActorRef[Response]) extends InputDataEvent
  final case class ReqAssetTypes(replyTo: ActorRef[Response]) extends InputDataEvent

  sealed trait Response
  final case class InitComplete() extends Response
  final case class RepOsm(osmModel: OsmModel) extends Response
  final case class RepAssetTypes(osmModel: OsmModel) extends Response

  def apply(): Behavior[InputDataEvent] = ???

}
