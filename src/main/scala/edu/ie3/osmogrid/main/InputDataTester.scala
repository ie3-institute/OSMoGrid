/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.main

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import edu.ie3.osmogrid.cfg.{ArgsParser, ConfigFailFast, OsmoGridConfig}
import edu.ie3.osmogrid.guardian.OsmoGridGuardian
import edu.ie3.osmogrid.guardian.OsmoGridGuardian.Run
import edu.ie3.osmogrid.io.input.OsmSource.PbfFileSource
import edu.ie3.osmogrid.main.HelloWorldMain.Test
import edu.ie3.osmogrid.model.OsmoGridModel.LvOsmoGridModel
import edu.ie3.osmogrid.model.{OsmoGridModel, PbfFilter}
import edu.ie3.osmogrid.model.PbfFilter.{Filter, LvFilter}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object Tester {

  def main(args: Array[String]): Unit = {

    val actorSystem: ActorSystem[Test] =
      ActorSystem(HelloWorldMain(), "OSMoGridGuardian")

  }

}

object HelloWorldMain {
  sealed trait Test

  def apply(): Behavior[Test] =
    Behaviors.setup { context =>
      val source = PbfFileSource(
        "InputData/OsmData/inputTest.pbf",
        context
      )

      val buildingFilter = Filter("building", Set.empty)
      val highwayFilter = Filter("highway", Set.empty)
      val landuseFilter = Filter("landuse", Set.empty)

      //      val pbf: Future[OsmoGridModel] =
      //        source.read(
      //          LvFilter(
      //            buildingFilter,
      //            highwayFilter,
      //            landuseFilter,
      //            PbfFilter.substationFilter
      //          )
      //        )

      val u = Await.result(
        source.read(
          LvFilter(
            buildingFilter,
            highwayFilter,
            landuseFilter,
            PbfFilter.standardBoundaryFilter,
            PbfFilter.substationFilter
          )
        ),
        Duration.Inf
      )

      println(
        s"${u.asInstanceOf[LvOsmoGridModel].landuses.size} landuses found. Expected number of landuses: 38"
      )
      println(
        s"${u.asInstanceOf[LvOsmoGridModel].highways.size} highways found. Expected number of highways: 1424"
      )
      println(
        s"${u.asInstanceOf[LvOsmoGridModel].buildings.size} buildings found. Expected number of buildings: 2512"
      )
      println(
        s"${u.asInstanceOf[LvOsmoGridModel].boundaries.size} boundaries found. Expected number of boundaries: 7"
      )
      println(
        s"${u.asInstanceOf[LvOsmoGridModel].allNodes().size} nodes found. Expected number of nodes: 16765"
      )

      Behaviors.stopped

    }
}
