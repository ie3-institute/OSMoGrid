/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import edu.ie3.datamodel.models.input.connector.`type`.LineTypeInput
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.lv.LvGraphGeneratorSupport.buildConnectedGridGraphs
import edu.ie3.osmogrid.lv.LvGridGeneratorSupport.buildGrid
import edu.ie3.osmogrid.model.OsmTestData
import edu.ie3.test.common.{GridSupport, UnitSpec}
import edu.ie3.util.quantities.QuantityUtils.RichQuantityDouble

import java.util.UUID
import scala.collection.parallel.CollectionConverters.ImmutableSeqIsParallelizable

class LvGridGeneratorSupportSpec
    extends UnitSpec
    with OsmTestData
    with GridSupport {

  "The LV grid generator support" should {

    "build an lv grid correctly" in {
      val osmoGridModel = TestLvOsmoGridModel.lvOsmoGridModel
      val powerDensity = 10.asKiloWattPerSquareMetre
      val minDistance = 0.002.asKilometre
      val considerBuildingConnections = false

      val (osmGraph, buildingGraphConnections) = buildConnectedGridGraphs(
        osmoGridModel,
        powerDensity,
        minDistance,
        considerBuildingConnections
      ).unzip match {
        case (Seq(osmGraph), Seq(buildingGraphConnections)) =>
          (osmGraph, buildingGraphConnections)
        case _ => fail("Expected exactly one graph.")
      }

      val ratedVoltage = 0.4.asKiloVolt
      val lineType = new LineTypeInput(
        UUID.randomUUID,
        "Default generated line type",
        0.0.asSiemensPerKilometre,
        0.07.asSiemensPerKilometre,
        0.32.asOhmPerKilometre,
        0.07.asOhmPerKilometre,
        235.0.asAmpere,
        0.4.asKiloVolt
      )

      buildGrid(
        osmGraph,
        buildingGraphConnections.par,
        ratedVoltage,
        10.asKiloVolt,
        considerHouseConnectionPoints = false,
        0.15,
        lineType,
        trafo_10kV_to_lv,
        "testGrid"
      ) match {
        case List(subGridContainer) =>
          subGridContainer.getRawGrid.getNodes.size() shouldBe 3
          subGridContainer.getRawGrid.getLines.size() shouldBe 1
          subGridContainer.getSystemParticipants.getLoads.size() shouldBe 2
        case Nil =>
          fail("No grid received!")
      }

    }
  }
}
