/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import edu.ie3.osmogrid.model.LoadLocation
import edu.ie3.util.geo.GeoUtils
import edu.ie3.util.osm.OsmModel
import net.morbz.osmonaut.osm.LatLon

import scala.jdk.CollectionConverters._

trait LoadClustering {

  def clusterLoads(
      loadLocations: Seq[LoadLocation[_]],
      osmContainer: OsmModel,
      restrictSubgridsToLanduseAreas: Boolean
  ): Unit = {
    /* Build sub groups if necessary */
    val subGroups = if (restrictSubgridsToLanduseAreas) {
      /* There shall be sub groups per land use area */
      OsmModel
        .extractLandUses(osmContainer.ways)
        .map(landUseArea =>
          loadLocations.filter(loadLocation =>
            // TODO: Check if coordinate is inside landuse, whenever the PowerSystemUtils are ready
            true
          )
        )
    } else {
      Seq(loadLocations)
    }

    // TODO: Build convex hulls of each sub group
  }
}
