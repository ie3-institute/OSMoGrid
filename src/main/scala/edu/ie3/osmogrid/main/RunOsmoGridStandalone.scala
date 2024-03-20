/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.main

import org.apache.pekko.actor.typed.ActorSystem
import edu.ie3.osmogrid.cfg.{ArgsParser, OsmoGridConfig}
import edu.ie3.osmogrid.guardian.{GuardianRequest, OsmoGridGuardian, Run}

object RunOsmoGridStandalone {

  def main(args: Array[String]): Unit = {
    val cfg: OsmoGridConfig = ArgsParser.prepare(args)

    val actorSystem = ActorSystem(OsmoGridGuardian(), "OSMoGridGuardian")
    actorSystem ! Run(cfg)
  }
}
