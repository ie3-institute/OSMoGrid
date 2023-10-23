/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.utils

import edu.ie3.datamodel.models.input.NodeInput
import edu.ie3.datamodel.models.voltagelevels.GermanVoltageLevelUtils._
import edu.ie3.osmogrid.guardian.run.RunGuardian
import edu.ie3.test.common.{GridSupport, UnitSpec}
import utils.{GridContainerUtils, VoltageUtils}

class GridContainerUtilsSpec extends UnitSpec with GridSupport {
  "GridContainerUtils" should {
    "filter lv subgrid container correctly" in {
      val lvGrid1 = mockSubGrid(1, MV_10KV, LV)
      val lvGrid2 = mockSubGrid(2, MV_20KV, LV)
      val lvGrid3 = mockSubGrid(3, MV_10KV, LV)

      GridContainerUtils.filterLv(Seq(lvGrid1)).size shouldBe 1
      GridContainerUtils.filterLv(Seq(lvGrid1, lvGrid2)).size shouldBe 1
      GridContainerUtils
        .filterLv(Seq(lvGrid1, lvGrid2, lvGrid3))
        .size shouldBe 2
    }

    "filter hv subgrid container correctly" in {
      val hvGrid1 = mockSubGrid(1, HV, MV_20KV)
      val hvGrid2 = mockSubGrid(2, HV, MV_10KV)
      val hvGrid3 = mockSubGrid(3, HV, MV_10KV)

      GridContainerUtils.filterLv(Seq(hvGrid1)).size shouldBe 0
      GridContainerUtils.filterLv(Seq(hvGrid1, hvGrid2)).size shouldBe 1
      GridContainerUtils
        .filterLv(Seq(hvGrid1, hvGrid2, hvGrid3))
        .size shouldBe 2
    }

    "return mv nodes correctly" in {
      val getNodes = PrivateMethod[Seq[NodeInput]](Symbol("getNodes"))
      val voltLevels = VoltageUtils.parse(RunGuardian.DEFAULT.mv)

      val lvGrid1 = mockSubGrid(1, MV_10KV, LV)
      val lvGrid2 = mockSubGrid(2, MV_20KV, LV)
      val lvGrid3 = mockSubGrid(3, MV_10KV, LV)

      val hvGrid1 = mockSubGrid(1, HV, MV_20KV)
      val hvGrid2 = mockSubGrid(2, HV, MV_10KV)
      val hvGrid3 = mockSubGrid(3, HV, MV_10KV)

      val nodes = GridContainerUtils invokePrivate getNodes(
        voltLevels,
        Seq(lvGrid1, lvGrid2, lvGrid3, hvGrid1, hvGrid2, hvGrid3)
      )

      nodes.size shouldBe 4
    }
  }
}
