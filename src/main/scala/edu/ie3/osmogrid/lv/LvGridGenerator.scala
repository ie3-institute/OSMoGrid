/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.LazyLogging
import de.osmogrid.util.OsmoGridUtils
import edu.ie3.datamodel.graph.DistanceWeightedEdge
import edu.ie3.datamodel.models.BdewLoadProfile
import edu.ie3.datamodel.models.input.connector.`type`.LineTypeInput
import edu.ie3.datamodel.models.input.{MeasurementUnitInput, NodeInput}
import edu.ie3.datamodel.models.input.connector.{
  LineInput,
  SwitchInput,
  Transformer2WInput,
  Transformer3WInput
}
import edu.ie3.datamodel.models.input.container.{
  GraphicElements,
  JointGridContainer,
  RawGridElements,
  SubGridContainer,
  SystemParticipants
}
import edu.ie3.datamodel.models.input.graphics.{
  LineGraphicInput,
  NodeGraphicInput
}
import edu.ie3.datamodel.models.input.system.characteristic.{
  CosPhiFixed,
  OlmCharacteristicInput
}
import edu.ie3.datamodel.models.input.system.{
  BmInput,
  ChpInput,
  EvInput,
  EvcsInput,
  FixedFeedInInput,
  HpInput,
  LoadInput,
  PvInput,
  StorageInput,
  WecInput
}
import edu.ie3.datamodel.models.voltagelevels.{
  GermanVoltageLevelUtils,
  VoltageLevel
}
import edu.ie3.datamodel.utils.GridAndGeoUtils
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.cfg.OsmoGridConfig.Lvgrid
import edu.ie3.osmogrid.exception.MissingOsmDataException
import edu.ie3.osmogrid.graph.{OsmGraph, OsmGridNode}
import edu.ie3.osmogrid.model.OsmoGridModel
import edu.ie3.osmogrid.model.OsmoGridModel.LvOsmoGridModel
import edu.ie3.util.OneToOneMap
import edu.ie3.util.geo.GeoUtils
import edu.ie3.util.geo.GeoUtils.buildCoordinate
import edu.ie3.util.geo.RichGeometries.RichPolygon
import edu.ie3.util.geo.RichGeometries.RichCoordinate
import edu.ie3.util.osm.OsmUtils.GeometryUtils.buildPolygon
import edu.ie3.util.osm.model.OsmEntity.{Node, Way}
import edu.ie3.util.osm.model.OsmEntity.Way.ClosedWay
import edu.ie3.util.quantities.PowerSystemUnits
import edu.ie3.util.quantities.interfaces.Irradiance
import org.jgrapht.graph.AsSubgraph
import org.locationtech.jts.geom.{Coordinate, Polygon}
import org.locationtech.jts.math.Vector2D
import org.slf4j.{Logger, LoggerFactory}
import tech.units.indriya.ComparableQuantity
import tech.units.indriya.quantity.Quantities
import tech.units.indriya.unit.Units

import collection.JavaConverters.asScalaSetConverter
import scala.jdk.CollectionConverters
import java.util
import java.util.UUID
import javax.measure.Quantity
import javax.measure.quantity.{Area, Energy, Length, Power}
import scala.collection.parallel.ParSeq
import scala.math.BigDecimal.RoundingMode
import scala.util.{Failure, Success, Try}
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.osmogrid.lv.GraphBuildingSupport.BuildingGraphConnection
import edu.ie3.util.quantities.interfaces.{Irradiance, PowerDensity}
import tech.units.indriya.ComparableQuantity
import edu.ie3.osmogrid.model.OsmoGridModel.LvOsmoGridModel

import javax.measure.quantity.{Area, Length, Power}

object LvGridGenerator extends GraphBuildingSupport with LazyLogging {
  sealed trait Request
  final case class GenerateGrid(
      osmData: LvOsmoGridModel,
      powerDensity: Irradiance,
      minDistance: ComparableQuantity[Length]
  ) extends Request

  sealed trait Response
  final case class RepLvGrid(
      grid: SubGridContainer
  ) extends Response

  private val nodeCodeMaps =
    new util.HashMap[Integer, OneToOneMap[String, Integer]]

  def apply(): Behaviors.Receive[Request] = idle

  private def idle: Behaviors.Receive[Request] = Behaviors.receive {
    case (ctx, GenerateGrid(osmData, powerDensity, minDistance)) =>
      val (graph, buildingGraphConnections) =
        buildGridGraph(osmData, powerDensity, minDistance)
      ???
    case (ctx, unsupported) =>
      ctx.log.warn(s"Received unsupported message '$unsupported'.")
      Behaviors.stopped
  }

  /** Generates a GridInputModel for each sub graph in graphModel
    *
    * @param graphModel
    *   GraphModel from which the GridInputModels shall be generated
    */
  private def buildGridOld(
      config: OsmoGridConfig,
      graphModel: List[AsSubgraph[OsmGridNode, DistanceWeightedEdge]],
      ratedVoltage: Double,
      voltageLevel: String,
      lineType: LineTypeInput
  ) = {
    val vRated = Quantities.getQuantity(
      ratedVoltage,
      PowerSystemUnits.KILOVOLT
    )

    val vTarget = Quantities.getQuantity(1d, PowerSystemUnits.PU)
    val voltLvl: VoltageLevel = Try(
      GermanVoltageLevelUtils.parse(voltageLevel, vRated)
    ) match {
      case Success(voltageLevel: VoltageLevel) => voltageLevel
      case Failure(e) =>
        log.error(
          "Could not set voltage level from config file. Continue with {}",
          voltageLevel,
          e
        )
        GermanVoltageLevelUtils.LV
    }
    // TODO Refactor
    // set id counters
    var nodeIdCounter = 1
    var lineIdCounter = 1
    var loadIdCounter = 1
    var subNetCounter = 1

    val nodeInputs = new util.HashSet[NodeInput]
    val loadInputs = new util.HashSet[LoadInput]
    val lineInputs = new util.HashSet[LineInput]

    for (subgraph <- graphModel) {
      val geoGridNodesMap =
        new collection.mutable.HashMap[OsmGridNode, NodeInput]
      for (osmGridNode: OsmGridNode <- subgraph.vertexSet().asScala) {
        if (osmGridNode.getLoad != null) {
          val nodeInput: NodeInput = new NodeInput(
            UUID.randomUUID,
            "Node " + {
              nodeIdCounter += 1
              nodeIdCounter - 1
            },
            vTarget,
            osmGridNode.isSubStation,
            GeoUtils.buildPoint(
              GeoUtils.buildCoordinate(
                osmGridNode.getLatlon.getLat,
                osmGridNode.getLatlon.getLon
              )
            ),
            voltLvl,
            subNetCounter
          )
          geoGridNodesMap.put(osmGridNode, nodeInput)
          nodeInputs.add(nodeInput)

          if (config.lvGrid.considerHouseConnectionPoints) {
            // If parameter considerHouseConnectionPoints is set to true, create another NodeInput at the nodes house connection point.
            val houseConnectionPoint: NodeInput = new NodeInput(
              UUID.randomUUID,
              "Node " + {
                nodeIdCounter += 1
                nodeIdCounter - 1
              },
              vTarget,
              false,
              GeoUtils.buildPoint(
                GeoUtils.buildCoordinate(
                  osmGridNode.getHouseConnectionPoint.getLat,
                  osmGridNode.getHouseConnectionPoint.getLon
                )
              ),
              voltLvl,
              subNetCounter
            )
            // The load will then be set to the node that represents the house connection point.
            val eConsAnnual: ComparableQuantity[Energy] =
              Quantities.getQuantity(
                osmGridNode.getLoad.toSystemUnit.getValue.doubleValue,
                PowerSystemUnits.KILOWATTHOUR
              )
            val loadInput: LoadInput = new LoadInput(
              UUID.randomUUID,
              "Load " + loadIdCounter,
              houseConnectionPoint,
              CosPhiFixed.CONSTANT_CHARACTERISTIC,
              BdewLoadProfile.H0,
              false,
              eConsAnnual,
              osmGridNode.getLoad
                .to(PowerSystemUnits.KILOVOLTAMPERE)
                .asInstanceOf[ComparableQuantity[Power]],
              1d
            )
            // TODO: do this with buildDefaultlineType or external function
            // Create a LineInput between the NodeInputs from the OsmogridNode and the house connection point

            val length: ComparableQuantity[Length] =
              GeoUtils.calcHaversine( // TODO: = weightededge of Graph?
                nodeInput.getGeoPosition.getY,
                nodeInput.getGeoPosition.getX,
                houseConnectionPoint.getGeoPosition.getY,
                houseConnectionPoint.getGeoPosition.getX
              )
            val lineInput: LineInput = new LineInput(
              UUID.randomUUID,
              "Line " + {
                lineIdCounter += 1
                lineIdCounter - 1
              },
              nodeInput,
              houseConnectionPoint,
              1,
              lineType,
              length,
              GridAndGeoUtils.buildSafeLineStringBetweenNodes(
                nodeInput,
                houseConnectionPoint
              ),
              OlmCharacteristicInput.CONSTANT_CHARACTERISTIC
            )
            lineInputs.add(lineInput)
            nodeInputs.add(houseConnectionPoint)
            loadInputs.add(loadInput)
          } else { // If parameter considerHouseConnectionPoints is set to false, solely create the load and set it to the NodeInput.
            val eConsAnnual: ComparableQuantity[Energy] =
              Quantities.getQuantity(
                osmGridNode.getLoad.toSystemUnit.getValue.doubleValue,
                PowerSystemUnits.KILOWATTHOUR
              )
            val loadInput: LoadInput = new LoadInput(
              UUID.randomUUID,
              "Load " + {
                loadIdCounter += 1
                loadIdCounter - 1
              },
              nodeInput,
              CosPhiFixed.CONSTANT_CHARACTERISTIC,
              BdewLoadProfile.H0,
              false,
              eConsAnnual,
              osmGridNode.getLoad
                .to(PowerSystemUnits.KILOVOLTAMPERE)
                .asInstanceOf[ComparableQuantity[Power]],
              1d
            )
            loadInputs.add(loadInput)
          }
        } else {
          if (subgraph.degreeOf(osmGridNode) > 2) { // If the node is an intersection, create a NodeInput
            val nodeInput: NodeInput = new NodeInput(
              UUID.randomUUID,
              "Node " + {
                nodeIdCounter += 1
                nodeIdCounter - 1
              },
              vTarget,
              osmGridNode.isSubStation,
              GeoUtils.buildPoint(
                GeoUtils.buildCoordinate(
                  osmGridNode.getLatlon.getLat,
                  osmGridNode.getLatlon.getLon
                )
              ),
              voltLvl,
              subNetCounter
            )
            geoGridNodesMap.put(osmGridNode, nodeInput)
            nodeInputs.add(nodeInput)
          }
        }
        // Depth-first-search for adding LineInputModels:
        val lineBuilder: LineBuilder = new LineBuilder(lineType, subgraph)
        lineBuilder.initialize(subgraph, geoGridNodesMap, lineIdCounter)
        lineBuilder.start()
        // add all lines from Gridbuilder to list
        lineInputs.addAll(lineBuilder.getLineInputModels)
        // refresh lineIdCounter
        lineIdCounter = lineBuilder.getLineIdCounter
        subNetCounter += 1
      }
    }
    // Build and return a JointGridContainer
    buildGridContainer(
      config.runtime,
      nodeInputs,
      lineInputs,
      loadInputs
    )
  }

  /** Builds a GridContainer by adding all assets together
    */

  private def buildGridContainer(
      config: OsmoGridConfig.Runtime,
      nodes: java.util.Set[NodeInput],
      lines: java.util.Set[LineInput],
      loads: java.util.Set[LoadInput]
  ) = {
    val rawGridElements = new RawGridElements(
      nodes,
      lines,
      new util.HashSet[Transformer2WInput],
      new util.HashSet[Transformer3WInput],
      new util.HashSet[SwitchInput],
      new util.HashSet[MeasurementUnitInput]
    )
    val systemParticipants = new SystemParticipants(
      new util.HashSet[BmInput],
      new util.HashSet[ChpInput],
      new util.HashSet[EvcsInput],
      new util.HashSet[EvInput],
      new util.HashSet[FixedFeedInInput],
      new util.HashSet[HpInput],
      loads,
      new util.HashSet[PvInput],
      new util.HashSet[StorageInput],
      new util.HashSet[WecInput]
    )
    val graphicElements = new GraphicElements(
      new util.HashSet[NodeGraphicInput],
      new util.HashSet[LineGraphicInput]
    )
    new JointGridContainer(
      config.name,
      rawGridElements,
      systemParticipants,
      graphicElements
    )
  }

  /** Calls the methods that are necessary for building the complete electrical
    * grid out of a GraphModel.
    *
    * @param graphModel
    *   GraphModel that was generated in the GraphController.
    * @return
    *   Returns the complete electrical grid as a List of GridInputModels (one
    *   for each subnet).
    */
  def generateGrid(
      config: OsmoGridConfig,
      graphModel: List[AsSubgraph[OsmGridNode, DistanceWeightedEdge]],
      lineType: LineTypeInput
  ): JointGridContainer = {
    val gridModel = buildGridOld(
      config,
      graphModel,
      config.lvgrid.ratedVoltage,
      config.lvgrid.voltageLevel,
      lineType
    )
    // build node code maps and admittance matrices for each sub net
    for (subGrid <- gridModel.getSubGridTopologyGraph.vertexSet.asScala) {
      val nodeCodeMap: OneToOneMap[String, Integer] =
        OsmoGridUtils.buildNodeCodeMap(subGrid.getRawGrid.getNodes)
      LvGridGenerator.nodeCodeMaps.put(subGrid.getSubnet, nodeCodeMap)
    }
    gridModel
  }
}
