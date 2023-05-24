/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import edu.ie3.datamodel.models.input.connector.`type`.LineTypeInput
import edu.ie3.osmogrid.lv.LvGraphGeneratorSupport.buildConnectedGridGraphs
import edu.ie3.osmogrid.lv.LvGridGeneratorSupport.buildGrid
import edu.ie3.osmogrid.model.OsmTestData
import edu.ie3.test.common.UnitSpec
import edu.ie3.util.quantities.QuantityUtils.RichQuantityDouble

import java.util.UUID
import scala.collection.parallel.CollectionConverters.ImmutableSeqIsParallelizable

class LvGridGeneratorSupportSpec extends UnitSpec with OsmTestData {

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

      val ratedVoltage = 10.asKiloVolt
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

      val subgridContainer = buildGrid(
        osmGraph,
        buildingGraphConnections.par,
        ratedVoltage,
        considerHouseConnectionPoints = false,
        lineType,
        "testGrid"
      )
      subgridContainer.getRawGrid.getNodes.size() shouldBe 2
      subgridContainer.getRawGrid.getLines.size() shouldBe 1
      subgridContainer.getSystemParticipants.getLoads.size() shouldBe 2

      // Todo: Extend test by connecting another house at node 22L
    }
  }
}
