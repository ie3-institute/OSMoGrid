/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import akka.actor.typed.scaladsl.Behaviors
import de.osmogrid.util.{OsmoGridUtils, TypeSourceFormat}
import de.osmogrid.util.TypeSourceFormat.TypeSourceFormat
import edu.ie3.datamodel.graph.{DistanceWeightedEdge, DistanceWeightedGraph}
import edu.ie3.datamodel.io.naming.FileNamingStrategy
import edu.ie3.datamodel.io.source.TypeSource
import edu.ie3.datamodel.io.source.csv.CsvTypeSource
import edu.ie3.datamodel.models.BdewLoadProfile
import edu.ie3.datamodel.models.input.{MeasurementUnitInput, NodeInput}
import edu.ie3.datamodel.models.input.connector.{LineInput, SwitchInput, Transformer2WInput, Transformer3WInput}
import edu.ie3.datamodel.models.input.connector.`type`.LineTypeInput
import edu.ie3.datamodel.models.input.container.{GraphicElements, JointGridContainer, RawGridElements, SubGridContainer, SystemParticipants}
import edu.ie3.datamodel.models.input.graphics.{LineGraphicInput, NodeGraphicInput}
import edu.ie3.datamodel.models.input.system.characteristic.{CosPhiFixed, OlmCharacteristicInput}
import edu.ie3.datamodel.models.input.system.{BmInput, ChpInput, EvInput, EvcsInput, FixedFeedInInput, HpInput, LoadInput, PvInput, StorageInput, WecInput}
import edu.ie3.datamodel.models.voltagelevels.{CommonVoltageLevel, GermanVoltageLevelUtils, VoltageLevel}
import edu.ie3.datamodel.utils.GridAndGeoUtils
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.cfg.OsmoGridConfig.Grid.{lineType, voltageLevel}
import edu.ie3.osmogrid.graph.{OsmGraph, OsmGridNode}
import edu.ie3.osmogrid.model.OsmoGridModel.EnhancedOsmEntity
import edu.ie3.util.OneToOneMap
import edu.ie3.util.geo.GeoUtils
import edu.ie3.util.osm.model.OsmEntity.Way
import edu.ie3.util.osm.model.OsmEntity.Way.OpenWay
import edu.ie3.util.osm.model.OsmEntity.Node
import edu.ie3.util.quantities.PowerSystemUnits
import edu.ie3.util.quantities.PowerSystemUnits.KILOVOLT
import fastparse.internal.Logger
import org.jetbrains.annotations.NotNull
import org.jgrapht.graph.AsSubgraph
import org.jxmapviewer.viewer.GeoPosition
import org.locationtech.jts.geom.{Coordinate, Point}
import org.locationtech.jts.triangulate.quadedge.Vertex
import org.slf4j
import org.slf4j.LoggerFactory
import tech.units.indriya.quantity.Quantities
import geny.Generator.from
import org.locationtech.jts.operation.overlay.LineBuilder

import java.util.{HashMap, HashSet, List, Map, Set, UUID}
import javax.measure.Quantity
import javax.measure.quantity.{Dimensionless, ElectricPotential, Energy, Length, Power}
import tech.units.indriya.ComparableQuantity
import tech.units.indriya.unit.Units

import java.util
import scala.util.{Failure, Success, Try}
import scala.collection.parallel.{ParSeq, immutable}
import scala.jdk.CollectionConverters.*
import scala.language.postfixOps
import scala.reflect.ClassManifestFactory.Nothing

object LvGridGenerator {
  sealed trait Request

  sealed trait Response

  final case class RepLvGrid(grid: SubGridContainer) extends Response

  val logger: slf4j.Logger = LoggerFactory.getLogger("LvGridGenerator")

  def apply(): Behaviors.Receive[Request] = idle

  private def idle: Behaviors.Receive[Request] = Behaviors.receive {
    case (ctx, unsupported) =>
      ctx.log.warn(s"Received unsupported message '$unsupported'.")
      Behaviors.stopped
  }

  private def buildStreetGraph(
      enhancedEntities: Seq[EnhancedOsmEntity]
  ): OsmGraph = {
    val graph = new OsmGraph()
    enhancedEntities.foreach(enhancedEntity => {
      // todo: unsafe
      val way = enhancedEntity.entity.asInstanceOf[Way]
      val nodeIds = way.nodes
      for (i <- 1 until nodeIds.size) {
        // todo: unsafe
        val nodeA =
          enhancedEntity.subEntities(nodeIds(i - 1)).asInstanceOf[Node]
        val nodeB = enhancedEntity.subEntities(nodeIds(i)).asInstanceOf[Node]
        graph.addVertex(nodeA)
        graph.addVertex(nodeB)
        // calculate edge weight
        val weight = GeoUtils.calcHaversine(
          nodeA.latitude,
          nodeA.longitude,
          nodeB.latitude,
          nodeB.longitude
        )
        // create edge and add edge to rawGraph
        val e = new DistanceWeightedEdge()
        graph.setEdgeWeight(e, weight.getValue.doubleValue)
        graph.addEdge(
          nodeA,
          nodeB,
          e
        ) // TODO: consider checking boolean from this method
      }
    })
    graph
  }

  /** Creates a default line type
    *
    * @return
    *   Default line type
    */

  private def builtDefaultLineType(): LineTypeInput = {
    new LineTypeInput(
      UUID.randomUUID,
      "Default generated line type",
      Quantities.getQuantity(0.0, PowerSystemUnits.SIEMENS_PER_KILOMETRE),
      Quantities.getQuantity(0.07, PowerSystemUnits.SIEMENS_PER_KILOMETRE),
      Quantities.getQuantity(0.32, PowerSystemUnits.OHM_PER_KILOMETRE),
      Quantities.getQuantity(0.07, PowerSystemUnits.OHM_PER_KILOMETRE),
      Quantities.getQuantity(235.0, Units.AMPERE),
      Quantities.getQuantity(0.4, PowerSystemUnits.KILOVOLT)
    )
  }

  /** Generates a GridInputModel for each sub graph in graphModel
    *
    * @param graphModel
    *   GraphModel from which the GridInputModels shall be generated
    */
  private def buildGrid(
      graphModel: List[AsSubgraph[Node, DistanceWeightedEdge]],
      ratedVoltage: Double,
      voltageLevel: String
  ) = {
    val vRated = Quantities.getQuantity(
      ratedVoltage,
      PowerSystemUnits.KILOVOLT
    )
    val vTarget = Quantities.getQuantity(1d, PowerSystemUnits.PU)
    val voltLvl: VoltageLevel = Try(
      GermanVoltageLevelUtils.parse(voltageLevel, vRated)
    ) match {
      case Success(voltageLevel: VoltageLevel) => voltLvl
      case Failure(e) =>
        LvGridGenerator.logger.error(
          "Could not set voltage level from config file. Continue with {}",
          voltLvl,
          e
        )
        GermanVoltageLevelUtils.LV
    }
    //TODO Refactor
    // set id counters
    var nodeIdCounter = 1
    var lineIdCounter = 1
    var loadIdCounter = 1
    var subNetCounter = 1

    val nodeInputs = new util.HashSet[NodeInput]
    val loadInputs = new util.HashSet[LoadInput]
    val lineInputs = new util.HashSet[LineInput]

    for (subgraph <- graphModel.asScala) {
      val geoGridNodesMap = new util.HashMap[OsmGridNode, NodeInput]
      for (osmGridNode: OsmGridNode <- subgraph.vertexSet.asScala) {
        if (osmGridNode.getLoad != null) {
          val nodeInput: NodeInput = new NodeInput(
            UUID.randomUUID,
            "Node " + {
              nodeIdCounter += 1
              nodeIdCounter - 1
            },
            vTarget,
            osmGridNode.isSubStation,
            GeoUtils.buildPoint(GeoUtils.buildCoordinate(osmGridNode.getLatlon.getLat, osmGridNode.getLatlon.getLon)),
            voltLvl,
            subNetCounter
          )
          geoGridNodesMap.put(osmGridNode, nodeInput)
          nodeInputs.add(nodeInput)

          if (OsmoGridConfig.Grid.considerHouseConnectionPoints) {
            // If parameter considerHouseConnectionPoints is set to true, create another NodeInput at the nodes house connection point.
            val houseConnectionPoint: NodeInput = new NodeInput(
              UUID.randomUUID,
              "Node " + {
                nodeIdCounter += 1
                nodeIdCounter - 1
              },
              vTarget,
              false,
              GeoUtils.buildPoint(osmGridNode.getHouseConnectionPoint),
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
              GeoUtils.buildPoint(osmGridNode.getLatlon),
              voltLvl,
              subNetCounter
            )
            geoGridNodesMap.put(osmGridNode, nodeInput)
            nodeInputs.add(nodeInput)
          }
        }
        // Depth-first-search for adding LineInputModels:
        val lineBuilder: LineBuilder = new LineBuilder(lineType)
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
    buildGridContainer(nodeInputs, lineInputs, loadInputs)
  }

  /** Builds a GridContainer by adding all assets together
    */

  private def buildGridContainer(
      nodes: util.Set[NodeInput],
      lines: util.Set[LineInput],
      loads: util.Set[LoadInput]
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
      OsmoGridConfig.Runtime.name,
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
      graphModel: List[AsSubgraph[Node, DistanceWeightedEdge]]
  ): JointGridContainer = {
    //TODO: Give ratedVoltage and VoltageLevel
    val gridModel = buildGrid(graphModel,1.0,"lv")
    // build node code maps and admittance matrices for each sub net
    for (subGrid <- gridModel.getSubGridTopologyGraph.vertexSet.asScala) {
      val nodeCodeMap: OneToOneMap[String, Integer] =
        OsmoGridUtils.buildNodeCodeMap(subGrid.getRawGrid.getNodes)
      LvGridGenerator.nodeCodeMaps.put(subGrid.getSubnet, nodeCodeMap)
    }
    gridModel
  }
}
