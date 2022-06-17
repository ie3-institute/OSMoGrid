/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import edu.ie3.datamodel.models.input.container.JointGridContainer
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.osmogrid.lv.GraphBuildingSupport.BuildingGraphConnection
import edu.ie3.util.osm.model.OsmEntity.Node
import edu.ie3.util.quantities.QuantityUtils.RichQuantityDouble

import scala.jdk.CollectionConverters.*
import scala.collection.parallel.ParSeq

trait GridBuildingSupport {

  def buildGrid(
      osmGraph: OsmGraph,
      buildingGraphConnections: ParSeq[BuildingGraphConnection],
      config: OsmoGridConfig.LvGrid
  ): JointGridContainer = {

    val nodesWithBuildings: Map[Node, BuildingGraphConnection] =
      buildingGraphConnections.map(bgc => (bgc.graphConnectionNode, bgc)).toMap

    val vRated = config.ratedVoltage.asKiloVolt

    osmGraph.vertexSet().asScala.foreach(node => {

      if nodesWithBuildings contains node {

      }

    })

    // generate NodeInput for every node that has a load connected to it

    // name NodeInputs via their osm id?

    // consider house connection points?

    // if yes:

    // create NodeInput for house connection point (building center)

    // create line between NodeInput and house connection point

    // if no:

    // only create load and connect it to the node

    // if there is no load connected to node but node is an intersection

    // create NodeInput

  }

  def buildLine() = {

    // build line between nodes of graph

    // consider nodes with attached loads or intersections

    // other nodes are only for geoposition of line

  }

}
