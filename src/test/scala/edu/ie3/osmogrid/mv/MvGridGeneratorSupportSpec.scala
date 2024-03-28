/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.mv

import edu.ie3.datamodel.models.input.system.characteristic.OlmCharacteristicInput.CONSTANT_CHARACTERISTIC
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.osmogrid.mv.MvGridGeneratorSupport.buildGrid
import edu.ie3.test.common.{GridSupport, MvTestData, UnitSpec}
import utils.Solver

import scala.jdk.CollectionConverters._

class MvGridGeneratorSupportSpec
    extends UnitSpec
    with MvTestData
    with GridSupport {
  "The MvGridGeneratorSupport" should {
    "build a mv grid correctly" in {
      val graph: OsmGraph = Solver.solve(transitionPoint, connections)
      val subgrid =
        buildGrid(2, graph, nodeToHv, nodeConversion, assetInformation)

      subgrid.getSubnet shouldBe 2
      val nodes = subgrid.getRawGrid.getNodes.asScala
      val lines = subgrid.getRawGrid.getLines.asScala

      nodes shouldBe Seq(
        nodeToHv,
        nodeInMv1,
        nodeInMv2,
        nodeInMv3,
        nodeInMv4,
        nodeInMv5,
        nodeInMv6
      ).map { n => n.copy().subnet(2).build() }.toSet
      lines.size shouldBe 7

      lines.foreach { line =>
        line.getParallelDevices shouldBe 1
        line.getType shouldBe defaultLineTypeMv
        line.getGeoPosition.getStartPoint shouldBe line.getNodeA.getGeoPosition
        line.getGeoPosition.getEndPoint shouldBe line.getNodeB.getGeoPosition
        line.getOlmCharacteristic shouldBe CONSTANT_CHARACTERISTIC
      }
    }

  }
}
