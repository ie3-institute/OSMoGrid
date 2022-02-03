/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.main

import akka.actor.typed.ActorSystem
import edu.ie3.osmogrid.guardian.OsmoGridGuardian
import edu.ie3.osmogrid.guardian.OsmoGridGuardian.Run
import edu.ie3.osmogrid.cfg.{ArgsParser, ConfigFailFast, OsmoGridConfig}

object RunOsmoGridStandalone {

  def main(args: Array[String]): Unit = {
    val cfg: OsmoGridConfig = ArgsParser.prepare(args)
    ConfigFailFast.check(cfg)

    val actorSystem: ActorSystem[OsmoGridGuardian.Request] =
      ActorSystem(OsmoGridGuardian(), "OSMoGridGuardian")
    actorSystem ! Run(cfg)
  }
}
