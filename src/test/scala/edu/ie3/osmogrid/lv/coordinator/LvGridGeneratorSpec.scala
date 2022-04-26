package edu.ie3.osmogrid.lv.coordinator

import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.osmogrid.lv.LvGridGenerator
import edu.ie3.osmogrid.model.OsmTestData
import edu.ie3.test.common.UnitSpec

object LvGridGeneratorSpec extends UnitSpec with OsmTestData {

  "A lv grid generator spec" should {

    "build a street graph correctly" in {
      val buildStreetGraph = PrivateMethod[OsmGraph](Symbol("buildStreetGraph"))
      val waySeq = Seq(ways.highway1, ways.highway2, nodes.nodesMap)
      val actual = LvGridGenerator invokePrivate buildStreetGraph(ways, nodes.nodesMap)
      actual.vertexSet.length shouldBe 4
    }

    // what happens when not all ways are connected to one another?

    // do ways in Osmogrid that connect to one another share a node at which they connect

  }

}
