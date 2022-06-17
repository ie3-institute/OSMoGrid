/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import edu.ie3.datamodel.models.BdewLoadProfile
import edu.ie3.datamodel.models.input.NodeInput
import edu.ie3.datamodel.models.input.container.JointGridContainer
import edu.ie3.datamodel.models.input.system.LoadInput
import edu.ie3.datamodel.models.input.system.characteristic.CosPhiFixed
import edu.ie3.datamodel.models.voltagelevels.{GermanVoltageLevelUtils, VoltageLevel}
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.osmogrid.lv.GraphBuildingSupport.BuildingGraphConnection
import edu.ie3.util.osm.model.OsmEntity.Node
import edu.ie3.util.quantities.PowerSystemUnits
import edu.ie3.util.quantities.QuantityUtils.RichQuantityDouble
import org.locationtech.jts.geom.Point
import tech.units.indriya.ComparableQuantity

import java.util.UUID
import javax.measure.quantity.{Dimensionless, Power}
import scala.jdk.CollectionConverters.*
import scala.collection.parallel.{ParMap, ParSeq}

trait GridBuildingSupport {

  def buildGrid(
      osmGraph: OsmGraph,
      buildingGraphConnections: ParSeq[BuildingGraphConnection],
      config: OsmoGridConfig.LvGrid
  ): JointGridContainer = {

    val nodesWithBuildings: ParMap[Node, BuildingGraphConnection] =
      buildingGraphConnections.map(bgc => (bgc.graphConnectionNode, bgc)).toMap

    val vRated = config.ratedVoltage.asKiloVolt
    val vTarget = 1d.asPu
    val voltageLevel = GermanVoltageLevelUtils.parse(vRated)
    val nodeCreator = createNode(vTarget, voltageLevel)

    osmGraph
      .vertexSet()
      .asScala
      .foreach(osmNode => {

        nodesWithBuildings.get(osmNode) match
          case Some(buildingGraphConnection: BuildingGraphConnection) => {

            val node = nodeCreator(
              buildingGraphConnection
                .createNodeName(config.considerHouseConnectionPoints),
              osmNode.coordinate
            )

            if config.considerHouseConnectionPoints then
              val houseConnectionPoint: NodeInput = ???

            val load = new LoadInput(
              UUID.randomUUID,
              "Load of building: " + buildingGraphConnection.building.id.toString,
              houseConnectionPoint,
              CosPhiFixed.CONSTANT_CHARACTERISTIC,
              BdewLoadProfile.H0,
              false,
              // todo: What to do for econsannual?
              0,
              buildingGraphConnection.buildingPower,
              1d
            )
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

  def createNode(
      vTarget: ComparableQuantity[Dimensionless],
      voltageLevel: VoltageLevel
  )(id: String, coordinate: Point): NodeInput = {
    new NodeInput(
      UUID.randomUUID(),
      id,
      vTarget,
      false,
      coordinate,
      voltageLevel,
      1
    )
  }

  def buildLine() = {

    // build line between nodes of graph

    // consider nodes with attached loads or intersections

    // other nodes are only for geoposition of line

  }

}
