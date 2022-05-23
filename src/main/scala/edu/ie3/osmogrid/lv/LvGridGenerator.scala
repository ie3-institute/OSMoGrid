/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import akka.actor.typed.scaladsl.Behaviors
import akka.util.Collections
import edu.ie3.datamodel.graph.{DistanceWeightedEdge, DistanceWeightedGraph}
import edu.ie3.datamodel.models.StandardUnits
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.osmogrid.exception.MissingOsmDataException
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.osmogrid.lv.LvGridGenerator.getClosest
import edu.ie3.osmogrid.model.OsmoGridModel
import edu.ie3.osmogrid.model.OsmoGridModel.{EnhancedOsmEntity, LvOsmoGridModel}
import edu.ie3.util.geo.{GeoUtils, RichGeometries}
import edu.ie3.util.geo.GeoUtils.{buildCoordinate, calcHaversine}
import edu.ie3.util.osm.OsmUtils
import edu.ie3.util.osm.model.OsmEntity.Way
import edu.ie3.util.osm.model.OsmEntity.Way.{ClosedWay, OpenWay}
import edu.ie3.util.osm.model.OsmEntity.Node
import edu.ie3.util.quantities.interfaces.{Irradiance, PowerDensity}
import org.locationtech.jts.geom.{Coordinate, Polygon}
import org.locationtech.jts.math.{Vector2D, Vector3D}
import tech.units.indriya.ComparableQuantity
import edu.ie3.util.geo.RichGeometries.RichPolygon
import edu.ie3.util.geo.RichGeometries.RichCoordinate
import edu.ie3.util.osm.OsmUtils.GeometryUtils
import edu.ie3.util.osm.OsmUtils.GeometryUtils.buildPolygon
import edu.ie3.util.quantities.{PowerSystemUnits, QuantityUtil}
import tech.units.indriya.quantity.Quantities
import tech.units.indriya.unit.Units
import javax.measure.Unit
import java.util.UUID
import javax.measure.Quantity
import javax.measure.quantity.{Area, Length, Power}
import scala.collection.immutable.{AbstractSeq, LinearSeq}
import scala.collection.parallel.{ParSeq, immutable}
import scala.math.BigDecimal.RoundingMode
import scala.util.{Failure, Success, Try}
import scala.collection.parallel.immutable.ParVector

object LvGridGenerator {
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

  /** Resembles a calculated connection between a building and a grid. Every
    * building gets connected on the nearest point at the nearest highway
    * section. The connection point can either be a new node on mentioned
    * highway section or one of the two highway section nodes
    *
    * @param building
    *   the building to connect
    * @param center
    *   the building center
    * @param buildingPower
    *   the building power
    * @param highwayNodeA
    *   node a of the highway section at which the building gets connected
    * @param highwayNodeB
    *   node b of the highway section at which the building gets connected
    * @param graphConnectionNode
    *   the graph connection node
    */
  final case class BuildingGraphConnection(
      building: ClosedWay,
      center: Coordinate,
      buildingPower: ComparableQuantity[Power],
      highwayNodeA: Node,
      highwayNodeB: Node,
      graphConnectionNode: Node
  ) {

    /** Checks whether the graph connection node is a new node. If not it is one
      * of the highway sections
      *
      * @return
      *   whether the graph connection is a new node
      */
    def hasNewNode: Boolean = {
      (graphConnectionNode != highwayNodeA) && (graphConnectionNode != highwayNodeB)
    }
  }

  val logger: slf4j.Logger = LoggerFactory.getLogger("LvGridGenerator")

  def apply(): Behaviors.Receive[Request] = idle

  private def idle: Behaviors.Receive[Request] = Behaviors.receive {
    case (ctx, GenerateGrid(osmData, powerDensity, minDistance)) =>
      val streetGraph = buildGridGraph(osmData, powerDensity, minDistance)
      ???
    case (ctx, unsupported) =>
      ctx.log.warn(s"Received unsupported message '$unsupported'.")
      Behaviors.stopped
  }

  /** Builds an extended weighted street graph that resembles not only the
    * streets but also the building graph connections.
    *
    * @param osmoGridModel
    *   the osmo grid data for which to build the graph
    * @param powerDensity
    *   the power density of a household
    * @param minDistance
    *   the minimum distance above which to build new nodes on the street graph
    *   for building connections
    * @return
    */
  private def buildGridGraph(
      osmoGridModel: LvOsmoGridModel,
      powerDensity: ComparableQuantity[Irradiance],
      minDistance: ComparableQuantity[Length]
  ): (OsmGraph, ParSeq[BuildingGraphConnection]) = {
    val (highways, highwayNodes) =
      OsmoGridModel.filterForWays(osmoGridModel.highways)
    val (building, buildingNodes) =
      OsmoGridModel.filterForClosedWays(osmoGridModel.buildings)
    val (landuses, landUseNodes) =
      OsmoGridModel.filterForClosedWays(osmoGridModel.landuses)
    val buildingGraphConnections = calcBuildingGraphConnections(
      landuses,
      building,
      highways,
      highwayNodes ++ buildingNodes ++ landUseNodes,
      powerDensity,
      minDistance
    )
    val streetGraph = buildStreetGraph(highways, highwayNodes)
    (
      updateGraphWithBuildingConnections(streetGraph, buildingGraphConnections),
      buildingGraphConnections
    )
  }

  /** builds a weighted street graph out ways and nodes.
    *
    * @param ways
    *   the ways
    * @param nodes
    *   the nodes
    * @return
    *   the street graph
    */
  private def buildStreetGraph(
      ways: ParSeq[Way],
      nodes: Map[Long, Node]
  ): OsmGraph = {
    val graph = new OsmGraph()
    ways.foreach(way => {
      val nodeIds = way.nodes
      nodeIds.sliding(2).foreach { case Seq(nodeAId, nodeBId) =>
        (nodes.get(nodeAId), nodes.get(nodeBId)) match
          case (Some(nodeA), Some(nodeB)) =>
            graph.addVertex(nodeA)
            graph.addVertex(nodeB)
            graph.addWeightedEdge(nodeA, nodeB)

          case (None, _) =>
            throw IllegalArgumentException(
              s"Node $nodeAId of Way ${way.id} is not within our nodes mapping"
            )
          case (_, None) =>
            throw IllegalArgumentException(
              s"Node $nodeBId of Way ${way.id} is not within our nodes mapping"
            )
      }
    })
    graph
  }

  /** Calculates building graph connections of buildings to the nearest highway
    * sections
    *
    * @param landuses
    *   all landuses
    * @param buildings
    *   all buildings
    * @param highways
    *   all highways
    * @param nodes
    *   all nodes
    * @param powerDensity
    *   the average power density of a house
    * @param minDistance
    *   the minimum distance above which to build new nodes on the street graph
    *   for building connections
    * @return
    *   all building graph connections
    */
  private def calcBuildingGraphConnections(
      landuses: ParSeq[ClosedWay],
      buildings: ParSeq[ClosedWay],
      highways: ParSeq[Way],
      nodes: Map[Long, Node],
      powerDensity: ComparableQuantity[Irradiance],
      minDistance: ComparableQuantity[Length]
  ): ParSeq[BuildingGraphConnection] = {
    val landusePolygons = landuses.map(buildPolygon(_, nodes).get)
    buildings.flatMap(building => {
      val buildingPolygon = buildPolygon(building, nodes).get
      val buildingCenter: Coordinate = buildingPolygon.getCentroid.getCoordinate
      // check if building is inside residential area
      if (isInsideLanduse(buildingCenter, landusePolygons)) {

        val closest = highways.flatMap(highway => {
          // get closest to each highway section
          highway.nodes.sliding(2).map { case Seq(nodeAId, nodeBId) =>
            (nodes.get(nodeAId), nodes.get(nodeBId)) match {
              case (None, _) =>
                throw IllegalArgumentException(
                  s"Node $nodeAId is not within our nodes mapping"
                )
              case (_, None) =>
                throw IllegalArgumentException(
                  s"Node $nodeBId is not within our nodes mapping"
                )
              case (Some(nodeA), Some(nodeB)) =>
                val (distance, node) = getClosest(
                  nodeA,
                  nodeB,
                  buildingCenter,
                  minDistance
                ).getOrElse(
                  throw MissingOsmDataException(
                    s"Could not retrieve closest nodes for highway ${highway.id}"
                  )
                )
                (distance, node, nodeA, nodeB)
            }
          }
        })

        val closestOverall = closest minBy {
          _._1
        }
        // calculate load of house
        val load = calcPower(buildingPolygon.calcAreaOnEarth, powerDensity)
        Some(
          BuildingGraphConnection(
            building,
            buildingCenter,
            load,
            closestOverall._3,
            closestOverall._4,
            closestOverall._2
          )
        )
      } else None
    })
  }

  /** Checks whether or not the center of a building is within a landuse
    *
    * @param buildingCenter
    *   the building center
    * @param landuses
    *   the landuse
    * @return
    *   whether or not the center of a building is within a landuse
    */
  def isInsideLanduse(
      buildingCenter: Coordinate,
      landuses: ParSeq[Polygon]
  ): Boolean = {
    for (landuse <- landuses) {
      if (landuse.containsCoordinate(buildingCenter)) return true
    }
    false
  }

  /** Get closest point of the buildings center to the highway section spanning
    * linePtA and linePtB. If we find a point closer to the building center that
    * is not linePtA nor linePtB we only take it if it is sufficiently far away
    * (further than minDistance) otherwise we go with line point nearby to not
    * inflate the number of nodes.
    *
    * @param buildingCenter
    *   center coordinate of the building
    * @param wayNodeA
    *   point a of the way section
    * @param wayNodeB
    *   point b of the way section
    * @param nodes
    *   node id to node map
    * @param minDistance
    *   the minimum distance above which to build new nodes on the street graph
    *   for building connections
    * @return
    *   a Tuple of the distance and the point
    */
  private def getClosest(
      wayNodeA: Long,
      wayNodeB: Long,
      buildingCenter: Coordinate,
      nodes: Map[Long, Node],
      minDistance: ComparableQuantity[Length]
  ): Try[(ComparableQuantity[Length], Node)] =
    (nodes.get(wayNodeA), nodes.get(wayNodeB)) match {
      case (Some(nodeA), Some(nodeB)) =>
        getClosest(nodeA, nodeB, buildingCenter, minDistance)
      case (None, _) =>
        Failure(
          IllegalArgumentException(
            s"Node $wayNodeA is not within our nodes mapping"
          )
        )
      case (_, None) =>
        Failure(
          IllegalArgumentException(
            s"Node $wayNodeB is not within our nodes mapping"
          )
        )
    }

  /** Get closest point of the buildings center to the highway section spanning
    * linePtA and linePtB. If we find a point closer to the building center that
    * is not linePtA nor linePtB we only take it if it is sufficiently far away
    * (further than minDistance) otherwise we go with line point nearby to not
    * inflate the number of nodes.
    *
    * @param buildingCenter
    *   center coordinate of the building
    * @param wayNodeA
    *   node a of the way section
    * @param wayNodeB
    *   node b of the way section
    * @param minDistance
    *   the minimum distance above which to build new nodes on the street graph
    *   for building connections
    * @return
    *   a Tuple of the distance and the point
    */
  private def getClosest(
      wayNodeA: Node,
      wayNodeB: Node,
      buildingCenter: Coordinate,
      minDistance: ComparableQuantity[Length]
  ): Try[(ComparableQuantity[Length], Node)] = {
    val coordinateA = buildCoordinate(wayNodeA.latitude, wayNodeA.longitude)
    val coordinateB = buildCoordinate(wayNodeB.latitude, wayNodeB.longitude)
    val orthogonalPt =
      orthogonalProjection(coordinateA, coordinateB, buildingCenter)
    // if orthogonal point is on the line and far enough apart from the line points
    // take it since this is the closest we will get
    if (
      orthogonalPt.isBetween(
        coordinateA,
        coordinateB,
        1e-3
      ) && ((orthogonalPt haversineDistance coordinateA) isGreaterThan minDistance)
      && ((orthogonalPt haversineDistance coordinateB) isGreaterThan minDistance)
    ) {
      val closestNode = Node(
        id = UUID.randomUUID().getMostSignificantBits,
        latitude = orthogonalPt.y,
        longitude = orthogonalPt.x,
        tags = Map(),
        metaInformation = None
      )
      Success(buildingCenter.haversineDistance(orthogonalPt), closestNode)
    }
    // take the nearer point of the two line points
    else
      val coordinateADistance = buildingCenter.haversineDistance(coordinateA)
      val coordinateBDistance = buildingCenter.haversineDistance(coordinateB)
      if (coordinateADistance.isLessThan(coordinateBDistance))
        Success((coordinateADistance, wayNodeA))
      else Success((coordinateBDistance, wayNodeB))
  }

  private def orthogonalProjection(
      linePtA: Coordinate,
      linePtB: Coordinate,
      pt: Coordinate
  ): Coordinate = {
    orthogonalProjection(
      Vector2D.create(linePtA),
      Vector2D.create(linePtB),
      Vector2D.create(pt)
    ).toCoordinate
  }

  /** Calculate the orthogonal projection of a point onto a line. Credits to
    * Andrey Tyukin. Check out how and why this works here:
    * https://stackoverflow.com/questions/54009832/scala-orthogonal-projection-of-a-point-onto-a-line
    *
    * @param linePtA
    *   first point of the line
    * @param linePtB
    *   second point of the line
    * @param pt
    *   the point for which to calculate the projection
    * @return
    *   the projected point
    */
  @deprecated("Move to GeoUtils of the PowerSystemUtils")
  private def orthogonalProjection(
      linePtA: Vector2D,
      linePtB: Vector2D,
      pt: Vector2D
  ): Vector2D = {
    val v = pt.subtract(linePtA)
    val d = linePtB.subtract(linePtA)
    linePtA.add(d.multiply((v dot d) / d.lengthSquared()))
  }

  /** Calculates the power value of a household load based on the provided
    * building area and the provided average power density value and the
    * provided average household area size
    *
    * @param area
    *   area of the household
    * @param powerDensity
    *   average power per area
    */
  private def calcPower(
      area: ComparableQuantity[Area],
      powerDensity: ComparableQuantity[Irradiance]
  ): ComparableQuantity[Power] = {
    val power = area
      .to(Units.SQUARE_METRE)
      .multiply(powerDensity.to(PowerSystemUnits.WATT_PER_SQUAREMETRE))
      .asType(classOf[Power])
      .to(PowerSystemUnits.KILOWATT)
    round(power, 4)
  }

  @deprecated("Move to QuantityUtils of the PowerSystemUtils")
  private def round[T <: Quantity[T]](
      quantity: ComparableQuantity[T],
      decimals: Int
  ): ComparableQuantity[T] = {
    if (decimals < 0)
      throw IllegalArgumentException(
        "You can not round to negative decimal places."
      )
    val rounded = BigDecimal
      .valueOf(quantity.getValue.doubleValue())
      .setScale(decimals, RoundingMode.HALF_UP)
      .doubleValue
    Quantities.getQuantity(rounded, quantity.getUnit)
  }

  /** Updates the graph by adding the building graph connections and updating
    * the edges of the surrounding nodes
    *
    * @param graph
    *   the graph to update
    * @param buildingGraphConnections
    *   the building-graph connections to update with
    * @return
    *   the updated OsmGraph
    */
  private def updateGraphWithBuildingConnections(
      graph: OsmGraph,
      buildingGraphConnections: ParSeq[BuildingGraphConnection]
  ): OsmGraph = {
    buildingGraphConnections.foreach(bgc =>
      if (bgc.hasNewNode) {
        graph.addVertex(bgc.graphConnectionNode)
        graph.removeEdge(bgc.highwayNodeA, bgc.highwayNodeB)
        graph.addWeightedEdge(bgc.highwayNodeA, bgc.graphConnectionNode)
        graph.addWeightedEdge(bgc.graphConnectionNode, bgc.highwayNodeB)
      }
    )
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
