/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv.region_coordinator

import akka.actor.testkit.typed.scaladsl.{
  ActorTestKit,
  ScalaTestWithActorTestKit,
  TestProbe
}
import edu.ie3.osmogrid.cfg.{OsmoGridConfig, OsmoGridConfigFactory}
import edu.ie3.osmogrid.io.input
import edu.ie3.osmogrid.io.input.InputDataProvider
import edu.ie3.osmogrid.model.OsmoGridModel.LvOsmoGridModel
import edu.ie3.osmogrid.model.SourceFilter.LvFilter
import edu.ie3.test.common.UnitSpec

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

object LvRegionCoordinatorTestModel
    extends ScalaTestWithActorTestKit
    with UnitSpec {

  private val actorTestKit = ActorTestKit("LvRegionCoordinatorIT")

  lazy val (lvConfig, osmoGridModel) = readOsmModel()

  protected def readOsmModel()
      : (OsmoGridConfig.Generation.Lv, LvOsmoGridModel) = {
    val inputResource = getClass.getResource("DoBoCas.pbf")
    assert(inputResource != null)
    val resourcePath = getResourcePath("DoBoCas.pbf")

    val cfg = OsmoGridConfigFactory
      .parseWithoutFallback(
        s"""input.osm.pbf.file = "${resourcePath.replace("\\", "\\\\")}"
           |input.asset.file.directory = ${getResourcePath("/lv_assets")}
           |input.asset.file.separator = ","
           |input.asset.file.hierarchic = false
           |output.csv.directory = "output_file_path"
           |generation.lv.distinctHouseConnections = true""".stripMargin
      )
      .success
      .value

    val inputActor = actorTestKit.spawn(
      InputDataProvider(cfg.input)
    )

    val inputReply = TestProbe[input.Response]()

    inputActor ! input.ReqOsm(
      inputReply.ref,
      filter = LvFilter()
    )

    inputReply
      .expectMessageType[input.RepOsm](30 seconds)
      .osmModel match {
      case lvModel: LvOsmoGridModel => (cfg.generation.lv.value, lvModel)
    }
  }
}
