/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.main

import akka.actor.typed.ActorSystem
import edu.ie3.osmogrid.guardian.OsmoGridGuardian
import edu.ie3.osmogrid.guardian.OsmoGridGuardian.{OsmoGridGuardianEvent, Run}
import edu.ie3.osmogrid.cfg.OsmoGridConfig

object RunOsmoGridStandalone {

  def main(args: Array[String]): Unit = {
    val cfg: OsmoGridConfig = OsmoGridConfig()

    val actorSystem: ActorSystem[OsmoGridGuardianEvent] =
      ActorSystem(OsmoGridGuardian(), "OSMoGridGuardian")
    actorSystem ! Run(cfg)
    print("Foo")
  }
}
