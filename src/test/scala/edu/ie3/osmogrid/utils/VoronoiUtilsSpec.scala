/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.utils

import edu.ie3.test.common.{NodeInputSupport, UnitSpec}
import utils.VoronoiUtils.{VoronoiPolygon, createPolygons}

class VoronoiUtilsSpec extends UnitSpec with NodeInputSupport {
  "The VoronoiUtils" should {

    "create a voronoi diagram correctly" in {
      val hvToMvNodes = List(nodeToHv1, nodeToHv2)

      val polygons: List[VoronoiPolygon] = createPolygons(hvToMvNodes)

      polygons.size shouldBe 2

    }

  }
}
