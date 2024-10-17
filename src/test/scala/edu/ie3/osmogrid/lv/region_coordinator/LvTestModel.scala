/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv.region_coordinator

import edu.ie3.datamodel.models.input.connector.`type`.{
  LineTypeInput,
  Transformer2WTypeInput
}
import edu.ie3.osmogrid.cfg.{OsmoGridConfig, OsmoGridConfigFactory}
import edu.ie3.osmogrid.io.input._
import edu.ie3.osmogrid.model.OsmoGridModel.LvOsmoGridModel
import edu.ie3.osmogrid.model.SourceFilter.LvFilter
import edu.ie3.test.common.UnitSpec
import edu.ie3.util.quantities.QuantityUtils.RichQuantityDouble
import org.apache.pekko.actor.testkit.typed.scaladsl.{
  ActorTestKit,
  ScalaTestWithActorTestKit,
  TestProbe
}

import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

object LvTestModel extends ScalaTestWithActorTestKit with UnitSpec {

  private val actorTestKit = ActorTestKit("LvRegionCoordinatorIT")

  lazy val (lvConfig, osmoGridModel) = readOsmModel()
  val assetInformation: AssetInformation = AssetInformation(
    Seq(
      new LineTypeInput(
        UUID.randomUUID,
        "Default generated line type",
        0.0.asSiemensPerKilometre,
        0.07.asSiemensPerKilometre,
        0.32.asOhmPerKilometre,
        0.07.asOhmPerKilometre,
        235.0.asAmpere,
        0.4.asKiloVolt
      )
    ),
    Seq(
      new Transformer2WTypeInput(
        UUID.fromString("a0cbd90a-4e9f-47db-8dca-041d3a288f77"),
        "0.63 MVA 10/0.4 kV Dyn5 ASEA",
        1.7384731670445954.asOhm,
        9.36379511166658.asOhm,
        630.asKiloVoltAmpere,
        10.0.asKiloVolt,
        0.4.asKiloVolt,
        16500.0.asNanoSiemens,
        145.8952227629774.asNanoSiemens,
        2.5.asPercent,
        0.0.asDegreeGeom,
        false,
        0,
        -2,
        2
      )
    ),
    Seq.empty
  )

  lazy val (lvConfigThreeCounties, osmoGridModelHelgoland) =
    readOsmModelHelgoland()
  protected def readOsmModel()
      : (OsmoGridConfig.Generation.Lv, LvOsmoGridModel) = {
    val inputResource = getClass.getResource("DoBoCas.pbf")
    assert(inputResource != null)
    val resourcePath = getResourcePath("DoBoCas.pbf")

    val cfg = OsmoGridConfigFactory
      .parseWithoutFallback(
        s"""input.osm.pbf.file = "${resourcePath.replace("\\", "\\\\")}"
           |input.asset.file.directory = "${getResourcePath("/lv_assets")
            .replace("\\", "\\\\")}"
           |input.asset.file.separator = ","
           |input.asset.file.hierarchic = false
           |output.gridName = "test_grid"
           |output.csv.directory = "output_file_path"
           |generation.lv.averagePowerDensity = 12.5
           |generation.lv.ratedVoltage = 0.4
           |generation.lv.gridName = "testLvGrid"
           |generation.lv.considerHouseConnectionPoints = false
           |generation.lv.loadSimultaneousFactor = 0.15
           |generation.lv.boundaryAdminLevel.starting = 2
           |generation.lv.boundaryAdminLevel.lowest = 8
           |generation.lv.minDistance = 10
           |generation.mv.spawnMissingHvNodes = false
           |generation.mv.voltageLevel.id = mv
           |generation.mv.voltageLevel.default = 10.0
           |""".stripMargin
      )
      .success
      .value

    val inputActor = actorTestKit.spawn(
      InputDataProvider(cfg.input)
    )

    val inputReply = TestProbe[InputResponse]()

    inputActor ! ReqOsm(
      inputReply.ref,
      filter = LvFilter()
    )

    inputReply
      .expectMessageType[RepOsm](30 seconds)
      .osmModel match {
      case lvModel: LvOsmoGridModel => (cfg.generation.lv.value, lvModel)
    }
  }

  protected def readOsmModelHelgoland()
      : (OsmoGridConfig.Generation.Lv, LvOsmoGridModel) = {
    val inputResource = getClass.getResource("helgoland.pbf")
    assert(inputResource != null)
    val resourcePath = getResourcePath("helgoland.pbf")

    val cfg = OsmoGridConfigFactory
      .parseWithoutFallback(
        s"""input.osm.pbf.file = "${resourcePath.replace("\\", "\\\\")}"
         |input.asset.file.directory = "${getResourcePath("/lv_assets")
            .replace("\\", "\\\\")}"
         |input.asset.file.separator = ","
         |input.asset.file.hierarchic = false
         |output.gridName = "test_grid"
         |output.csv.directory = "output_file_path"
         |generation.lv.averagePowerDensity = 12.5
         |generation.lv.ratedVoltage = 0.4
         |generation.lv.gridName = "testLvGrid"
         |generation.lv.considerHouseConnectionPoints = false
         |generation.lv.loadSimultaneousFactor = 0.15
         |generation.lv.boundaryAdminLevel.starting = 2
         |generation.lv.boundaryAdminLevel.lowest = 8
         |generation.lv.minDistance = 10
         |generation.mv.spawnMissingHvNodes = false
         |generation.mv.voltageLevel.id = mv
         |generation.mv.voltageLevel.default = 10.0
         |""".stripMargin
      )
      .success
      .value

    val inputActor = actorTestKit.spawn(
      InputDataProvider(cfg.input)
    )

    val inputReply = TestProbe[InputResponse]()

    inputActor ! ReqOsm(
      inputReply.ref,
      filter = LvFilter()
    )

    inputReply
      .expectMessageType[RepOsm](30 seconds)
      .osmModel match {
      case lvModel: LvOsmoGridModel => (cfg.generation.lv.value, lvModel)
    }
  }
}
