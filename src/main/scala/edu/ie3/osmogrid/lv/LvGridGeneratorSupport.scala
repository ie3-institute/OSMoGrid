/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import com.typesafe.scalalogging.LazyLogging
import edu.ie3.datamodel.graph.DistanceWeightedEdge
import edu.ie3.datamodel.models.BdewLoadProfile
import edu.ie3.datamodel.models.input.{MeasurementUnitInput, NodeInput}
import edu.ie3.datamodel.models.input.connector.{
  LineInput,
  SwitchInput,
  Transformer2WInput,
  Transformer3WInput
}
import edu.ie3.datamodel.models.input.connector.`type`.LineTypeInput
import edu.ie3.datamodel.models.input.container.{
  GraphicElements,
  RawGridElements,
  SubGridContainer,
  SystemParticipants
}
import edu.ie3.datamodel.models.input.graphics.{
  LineGraphicInput,
  NodeGraphicInput
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
import edu.ie3.datamodel.models.input.system.characteristic.CosPhiFixed
import edu.ie3.datamodel.models.voltagelevels.{
  GermanVoltageLevelUtils,
  VoltageLevel
}
import edu.ie3.osmogrid.exception.IllegalStateException
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.osmogrid.lv.LvGraphGeneratorSupport.BuildingGraphConnection
import edu.ie3.util.geo.GeoUtils
import edu.ie3.util.osm.model.OsmEntity.Node
import edu.ie3.util.quantities.QuantityUtils.RichQuantityDouble
import org.locationtech.jts.geom.impl.CoordinateArraySequence
import org.locationtech.jts.geom.{LineString, Point}
import tech.units.indriya.ComparableQuantity
import java.util
import java.util.UUID
import javax.measure.quantity.{Dimensionless, ElectricPotential, Power}
import scala.annotation.tailrec
import scala.collection.Set
import scala.jdk.CollectionConverters._
import scala.collection.parallel.{ParMap, ParSeq}

object LvGridGeneratorSupport extends LazyLogging {

  /** Container to store built grid assets.
    *
    * @param nodes
    *   mapping from osm to electrical node
    * @param loads
    *   the built loads
    */
  final case class GridElements(
      nodes: Map[Node, NodeInput],
      loads: Set[LoadInput]
  ) {
    def +(load: LoadInput): GridElements = {
      GridElements(this.nodes, this.loads + load)
    }

    def ++(nodes: Map[Node, NodeInput]): GridElements = {
      GridElements(this.nodes ++ nodes, this.loads)
    }
  }

  /** Builds a [[SubGridContainer]] from an OSM street graph by traversing the
    * graph, building electrical nodes for all street nodes that are
    * intersection or have a connected building associated with it and building
    * electrical lines between all the electrical nodes.
    *
    * @param osmGraph
    *   the osm graph to traverse
    * @param buildingGraphConnections
    *   the building connections to the street graph
    * @param ratedVoltage
    *   the rated voltage of the grid to build
    * @param considerHouseConnectionPoints
    *   whether or not to build distinct lines to houses
    * @param lineType
    *   the line type for the electrical lines
    * @param gridName
    *   the name for the grid
    * @return
    *   the built [[SubGridContainer]]
    */
  def buildGrid(
      osmGraph: OsmGraph,
      buildingGraphConnections: ParSeq[BuildingGraphConnection],
      ratedVoltage: ComparableQuantity[ElectricPotential],
      considerHouseConnectionPoints: Boolean,
      lineType: LineTypeInput,
      gridName: String
  ): SubGridContainer = {

    val nodesWithBuildings: ParMap[Node, BuildingGraphConnection] =
      buildingGraphConnections.map(bgc => (bgc.graphConnectionNode, bgc)).toMap

    val vTarget = 1d.asPu
    val voltageLevel = GermanVoltageLevelUtils.parse(ratedVoltage)
    val nodeCreator = createNode(vTarget, voltageLevel) _

    val gridElements = osmGraph
      .vertexSet()
      .asScala
      .foldLeft(GridElements(Map(), Set()))((gridElements, osmNode) => {
        nodesWithBuildings.get(osmNode) match {
          case Some(buildingGraphConnection: BuildingGraphConnection)
              if buildingGraphConnection.isSubstation =>
            val substationNode = nodeCreator(
              "",
              osmNode.coordinate,
              true
            )
            gridElements ++ Map(osmNode -> substationNode)
          case Some(buildingGraphConnection: BuildingGraphConnection) =>
            val highwayNode = nodeCreator(
              buildingGraphConnection
                .createHighwayNodeName(
                  considerHouseConnectionPoints
                ),
              osmNode.coordinate,
              false
            )
            val loadCreator = createLoad(
              "Load of building: " + buildingGraphConnection.building.id.toString,
              buildingGraphConnection.buildingPower
            ) _
            if (considerHouseConnectionPoints) {
              val osmBuildingConnectionNode =
                buildingGraphConnection.buildingConnectionNode.getOrElse(
                  throw IllegalStateException(
                    s"Building node for building graph connection $buildingGraphConnection has to be present when considering building connections."
                  )
                )
              val buildingConnectionNode: NodeInput = nodeCreator(
                buildingGraphConnection.createBuildingNodeName(),
                osmBuildingConnectionNode.coordinate,
                false
              )
              val load = loadCreator(buildingConnectionNode)
              gridElements ++ Map(
                osmNode -> highwayNode,
                osmBuildingConnectionNode -> buildingConnectionNode
              ) + load

            } else {
              val load = loadCreator(highwayNode)
              gridElements ++ Map(osmNode -> highwayNode) + load
            }

          case None if osmGraph.degreeOf(osmNode) > 2 =>
            val node = nodeCreator(
              s"Node highway: ${osmNode.id}",
              osmNode.coordinate,
              false
            )
            gridElements ++ Map(osmNode -> node)
          case None =>
            gridElements
        }
      })
    val (startNode, startNodeInput) = gridElements.nodes.headOption.getOrElse(
      throw new IllegalArgumentException("No nodes were converted.")
    )
    val (visitedNodes, lineInputs) = traverseGraph(
      startNode,
      startNodeInput,
      osmGraph,
      Set.empty,
      Set.empty,
      gridElements.nodes,
      lineType
    )

    val unvisitedNodes = osmGraph.vertexSet().asScala.diff(visitedNodes)
    if (unvisitedNodes.nonEmpty) {
      logger.error(
        "We did not visit all nodes while taversing the graph. Unvisited Nodes: " + unvisitedNodes
      )
    }

    buildGridContainer(
      gridName,
      gridElements.nodes.values.toSet.asJava,
      lineInputs.asJava,
      gridElements.loads.asJava
    )
  }

  /** Create a node.
    *
    * @param vTarget
    *   the target voltage of the node
    * @param voltageLevel
    *   the voltage level
    * @param id
    *   the id of the node
    * @param coordinate
    *   the coordinate of the node position
    * @return
    *   the created node
    */
  private def createNode(
      vTarget: ComparableQuantity[Dimensionless],
      voltageLevel: VoltageLevel
  )(id: String, coordinate: Point, isSlack: Boolean): NodeInput = {
    new NodeInput(
      UUID.randomUUID(),
      id,
      vTarget,
      isSlack,
      coordinate,
      voltageLevel,
      1
    )
  }

  /** Creates a load.
    *
    * @param id
    *   the id for the load to build
    * @param ratedPower
    *   the rated power of the load
    * @param node
    *   the node at which the load will be connected
    * @return
    *   the created load
    */
  private def createLoad(id: String, ratedPower: ComparableQuantity[Power])(
      node: NodeInput
  ) =
    new LoadInput(
      UUID.randomUUID(),
      id,
      node,
      CosPhiFixed.CONSTANT_CHARACTERISTIC,
      BdewLoadProfile.H0,
      false,
      // todo: What to do for econsannual?
      0.asWattHour,
      ratedPower,
      1d
    )

  /** Recursively traverses the graph by starting at a given node and follows
    * all connected edges sequentially, building all lines between nodes in the
    * process.
    *
    * @param currentNode
    *   the osm node at which we start
    * @param currentNodeInput
    *   the node input associated with the osm node at which we start
    * @param osmGraph
    *   the osm graph wich we traverse
    * @param alreadyVisited
    *   nodes we have already visited
    * @param lines
    *   lines we have already built
    * @param nodeToNodeInput
    *   a mapping from osm node to node input
    * @param lineTypeInput
    *   the line type we use for building lines
    * @return
    *   a tuple of the set of already visited nodes and lines we have built
    */
  private def traverseGraph(
      currentNode: Node,
      currentNodeInput: NodeInput,
      osmGraph: OsmGraph,
      alreadyVisited: Set[Node],
      lines: Set[LineInput],
      nodeToNodeInput: Map[Node, NodeInput],
      lineTypeInput: LineTypeInput
  ): (Set[Node], Set[LineInput]) = {
    if (alreadyVisited.contains(currentNode)) return (alreadyVisited, lines)
    val connectedEdges = osmGraph.edgesOf(currentNode).asScala
    // traverse through every edge of the current node to build lines
    connectedEdges.foldLeft((alreadyVisited, lines)) {
      case ((updatedAlreadyVisited, updatedLines), edge) =>
        val nextNode = getOtherEdgeNode(osmGraph, currentNode, edge)
        if (!alreadyVisited.contains(nextNode)) {
          // follow the edge along until the next node input is found if there is any
          val (maybeNextNodeInput, maybeNextNode, passedStreetNodes) =
            findNextNodeInput(
              osmGraph,
              nextNode,
              edge,
              updatedAlreadyVisited + currentNode,
              nodeToNodeInput
            )
          maybeNextNodeInput.zip(maybeNextNode) match {
            // if a node input is found along the edge we build a line
            case Some((nextNodeInput, nextNode)) =>
              val newLine =
                buildLine(
                  currentNodeInput,
                  nextNodeInput,
                  // for building the line we want to consider the whole street section we went along
                  currentNode +: passedStreetNodes :+ nextNode,
                  lineTypeInput
                )
              val (visitedNodes, builtLines) = traverseGraph(
                nextNode,
                nextNodeInput,
                osmGraph,
                alreadyVisited ++ passedStreetNodes + currentNode,
                lines + newLine,
                nodeToNodeInput,
                lineTypeInput
              )
              (
                updatedAlreadyVisited ++ visitedNodes,
                updatedLines ++ builtLines
              )
            // if there is no more node input along the edge we are done with this branch of the graph
            case None =>
              (
                updatedAlreadyVisited ++ passedStreetNodes + currentNode,
                updatedLines
              )
          }
        } else {
          // we've already been at this node before so we are done on this branch of the graph
          (updatedAlreadyVisited, updatedLines)
        }
    }
  }

  /** Builds line between the nodes. Includes passed passed osm street node to
    * the geo position to track the street profile.
    *
    * @param firstNode
    *   node at which the line starts
    * @param secondNode
    *   node at which the line ends
    * @param streetNodes
    *   osm street nodes the line follows along
    * @param lineType
    *   type of the line to build
    * @return
    *   the built line
    */
  private def buildLine(
      firstNode: NodeInput,
      secondNode: NodeInput,
      passedStreetNodes: Seq[Node],
      lineType: LineTypeInput
  ): LineInput = {
    val lineGeoNodes = passedStreetNodes
      .map(_.coordinate.getCoordinate)
      .toArray
      .prepended(firstNode.getGeoPosition.getCoordinate)
      .appended(secondNode.getGeoPosition.getCoordinate)
    val geoPosition = new LineString(
      new CoordinateArraySequence(
        lineGeoNodes
      ),
      GeoUtils.DEFAULT_GEOMETRY_FACTORY
    )
    val id = s"Line between: " + passedStreetNodes.headOption
      .getOrElse(
        throw new IllegalArgumentException(
          s"Line between $firstNode and $secondNode has no first node."
        )
      )
      .id + "-" + passedStreetNodes.lastOption
      .getOrElse(
        throw new IllegalArgumentException(
          s"Line between $firstNode and $secondNode has no last node."
        )
      )
      .id
    new LineInput(
      UUID.randomUUID(),
      id,
      firstNode,
      secondNode,
      0,
      lineType,
      GeoUtils.calcHaversine(geoPosition),
      geoPosition,
      // todo: What do we expect as OlmCharacteristic?
      null
    )
  }

  /** Looks for the next [[Node]] that has an associated [[NodeInput]]. Returns
    * all passed [[Node]]s we passed during the search. Includes the node at
    * which we found a [[NodeInput]].
    *
    * @param graph
    *   graph we traverse
    * @param currentNode
    *   node at which we start
    * @param lastEdge
    *   edge from where we came
    * @param alreadyVisited
    *   nodes we already visited
    * @param nodeToNodeInput
    *   map of all created [[NodeInput]]s
    * @param passedNodes
    *   nodes we passed while traversing
    * @return
    *   An optional of the found [[NodeInput]], an optional of the associated
    *   [[Node]] and all [[Node]]s we looked at while traversin
    */
  @tailrec
  private def findNextNodeInput(
      graph: OsmGraph,
      currentNode: Node,
      lastEdge: DistanceWeightedEdge,
      alreadyVisited: Set[Node],
      nodeToNodeInput: Map[Node, NodeInput],
      passedNodes: Seq[Node] = Seq.empty
  ): (Option[NodeInput], Option[Node], Seq[Node]) = {
    if (alreadyVisited.contains(currentNode))
      return (None, None, passedNodes)
    nodeToNodeInput.get(currentNode) match {
      case Some(nodeInput) =>
        (Some(nodeInput), Some(currentNode), passedNodes)
      case None =>
        graph.edgesOf(currentNode).asScala.filter(_ != lastEdge).toSeq match {
          case Seq(nextEdge) =>
            val nextNode = getOtherEdgeNode(graph, currentNode, nextEdge)
            findNextNodeInput(
              graph,
              nextNode,
              nextEdge,
              alreadyVisited + currentNode,
              nodeToNodeInput,
              passedNodes :+ currentNode
            )
          case Nil =>
            // this means we arrived at a dead end at which no node is connected
            (None, None, passedNodes :+ currentNode)
          case seq =>
            throw IllegalStateException(
              s"Node: $currentNode has no associated node input but more than two associated edges. Edges: $seq"
            )
        }

    }
  }

  /** Returns the other node of an edge.
    *
    * @param graph
    *   the graph
    * @param source
    *   the source node
    * @param edge
    *   the considered edge
    * @return
    *   the node on the other side of the edge
    */
  private def getOtherEdgeNode(
      graph: OsmGraph,
      source: Node,
      edge: DistanceWeightedEdge
  ): Node = {
    if (graph.getEdgeSource(edge) == source) graph.getEdgeTarget(edge)
    else graph.getEdgeSource(edge)
  }

  /** Builds a GridContainer by adding all assets together
    */
  private def buildGridContainer(
      gridName: String,
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
    new SubGridContainer(
      gridName,
      1,
      rawGridElements,
      systemParticipants,
      graphicElements
    )
  }
}
