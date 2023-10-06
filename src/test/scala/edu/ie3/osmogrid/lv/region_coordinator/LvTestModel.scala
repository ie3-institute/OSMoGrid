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
import edu.ie3.datamodel.models.input.connector.`type`.{
  LineTypeInput,
  Transformer2WTypeInput
}
import edu.ie3.osmogrid.cfg.{OsmoGridConfig, OsmoGridConfigFactory}
import edu.ie3.osmogrid.io.input
import edu.ie3.osmogrid.io.input.InputDataProvider
import edu.ie3.osmogrid.io.input.AssetInformation
import edu.ie3.osmogrid.model.OsmoGridModel.LvOsmoGridModel
import edu.ie3.osmogrid.model.SourceFilter.LvFilter
import edu.ie3.test.common.UnitSpec
import edu.ie3.util.quantities.QuantityUtils.RichQuantityDouble
import org.scalatestplus.mockito.MockitoSugar.mock

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
    Seq(mock[Transformer2WTypeInput])
  )

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
           |output.gridName = "test_grid"
           |output.csv.directory = "output_file_path"
           |generation.lv.averagePowerDensity = 12.5
           |generation.lv.ratedVoltage = 0.4
           |generation.lv.gridName = "testLvGrid"
           |generation.lv.considerHouseConnectionPoints = false
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
