/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.util.geo

import edu.ie3.osmogrid.model.Coordinate
import edu.ie3.test.common.UnitSpec

class GeoUtilScalaSpec extends UnitSpec {
  "Providing useful means for geographic and geometric stuff" when {
    "calculating the enclosed area" should {
      "provide correct area for a rectangle" in {
        val coordinates = List((0, 0), (0, 5), (3, 5), (3, 0)).map {
          case (lon, lat) => Coordinate(lat, lon)
        }

        GeoUtilScala.enclosedArea(coordinates) shouldBe 15.0 +- 1e-3
      }

      "provide correct area for a triangle" in {
        val coordinates = List((0.0, 0.0), (0.0, 5.0), (3.0, 2.5)).map {
          case (lon, lat) => Coordinate(lat, lon)
        }

        GeoUtilScala.enclosedArea(coordinates) shouldBe 7.5 +- 1e-3
      }

      "provide correct area for a ditched rectangle" in {
        val coordinates =
          List((0.0, 0.0), (2.0, 2.5), (0.0, 5.0), (3.0, 5.0), (3.0, 0.0)).map {
            case (lon, lat) => Coordinate(lat, lon)
          }

        GeoUtilScala.enclosedArea(coordinates) shouldBe 10.0 +- 1e-3
      }
    }
  }
}
