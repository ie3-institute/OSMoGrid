/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import edu.ie3.datamodel.models.StandardUnits
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.osmogrid.lv.LvGraphBuilder.BuildingGraphConnection
import edu.ie3.osmogrid.model.{OsmTestData, OsmoGridModel}
import edu.ie3.test.common.UnitSpec
import tech.units.indriya.quantity.Quantities
import tech.units.indriya.unit.Units

import scala.collection.parallel.ParSeq

class LvGridGeneratorSpec extends UnitSpec with OsmTestData {

  "An Lv Grid Generator" should {

    "convert the osm graph to a correct subgrid" in {

      val osmoGridModel = TestLvOsmoGridModel.lvOsmoGridModel
      val powerDensity =
        Quantities.getQuantity(10, StandardUnits.SOLAR_IRRADIANCE)
      val minDistance = Quantities.getQuantity(10, Units.METRE)
      val considerBuildingGraphConnections = true
      val buildGridGraph =
        PrivateMethod[(OsmGraph, ParSeq[BuildingGraphConnection])](
          Symbol("buildGridGraph")
        )
      val (graph, buildingGraphConnections) =
        LvGridGenerator invokePrivate buildGridGraph(
          osmoGridModel,
          powerDensity,
          minDistance,
          considerBuildingGraphConnections
        )
      print("")

    }

  }

}
