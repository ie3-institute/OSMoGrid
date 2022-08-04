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
import edu.ie3.osmogrid.io.input.InputDataProvider
import edu.ie3.osmogrid.model.OsmoGridModel.LvOsmoGridModel
import edu.ie3.osmogrid.model.SourceFilter.LvFilter
import org.scalatest.{OptionValues, TryValues}

import java.nio.file.Paths
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

object LvRegionCoordinatorTestModel
    extends ScalaTestWithActorTestKit
    with OptionValues
    with TryValues {

  private val actorTestKit = ActorTestKit("LvRegionCoordinatorIT")

  lazy val (lvConfig, osmoGridModel) = readOsmModel()

  protected def readOsmModel()
      : (OsmoGridConfig.Generation.Lv, LvOsmoGridModel) = {
    val inputResource = getClass.getResource("DoBoCas.pbf")
    assert(inputResource != null)
    val resourcePath =
      Paths
        .get(inputResource.toURI)
        .toAbsolutePath
        .toString

    val cfg = OsmoGridConfigFactory
      .parseWithoutFallback(
        s"""input.osm.pbf.file = "${resourcePath.replace("\\", "\\\\")}"
           |input.asset.file.directory = "asset_input_dir"
           |input.asset.file.separator = ","
           |input.asset.file.hierarchic = false
           |output.gridName = "test_grid"
           |output.csv.directory = "output_file_path"
           |generation.lv.averagePowerDensity = 12.5
           |generation.lv.ratedVoltage = 0.4
           |generation.lv.considerHouseConnectionPoints = false
           |generation.lv.boundaryAdminLevel.starting = 2
           |generation.lv.boundaryAdminLevel.lowest = 8
           |""".stripMargin
      )
      .success
      .value

    val inputActor = actorTestKit.spawn(
      InputDataProvider(cfg.input)
    )

    val inputReply = TestProbe[InputDataProvider.Response]()

    inputActor ! InputDataProvider.ReqOsm(
      inputReply.ref,
      filter = LvFilter()
    )

    inputReply
      .expectMessageType[InputDataProvider.RepOsm](30 seconds)
      .osmModel match {
      case lvModel: LvOsmoGridModel => (cfg.generation.lv.value, lvModel)
    }
  }
}
