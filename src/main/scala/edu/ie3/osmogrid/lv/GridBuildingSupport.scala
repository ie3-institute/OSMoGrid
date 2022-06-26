/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import edu.ie3.datamodel.graph.DistanceWeightedEdge
import edu.ie3.datamodel.models.BdewLoadProfile
import edu.ie3.datamodel.models.input.NodeInput
import edu.ie3.datamodel.models.input.connector.LineInput
import edu.ie3.datamodel.models.input.connector.`type`.LineTypeInput
import edu.ie3.datamodel.models.input.container.JointGridContainer
import edu.ie3.datamodel.models.input.system.LoadInput
import edu.ie3.datamodel.models.input.system.characteristic.CosPhiFixed
import edu.ie3.datamodel.models.voltagelevels.{
  GermanVoltageLevelUtils,
  VoltageLevel
}
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.exception.{
  IllegalStateException,
  MissingOsmDataException
}
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.osmogrid.lv.GraphBuildingSupport.BuildingGraphConnection
import edu.ie3.osmogrid.lv.GridBuildingSupport.GridElements
import edu.ie3.util.osm.model.OsmEntity.Node
import edu.ie3.util.quantities.QuantityUtils.RichQuantityDouble
import org.locationtech.jts.geom.Point
import tech.units.indriya.ComparableQuantity

import java.util.UUID
import javax.measure.quantity.{Dimensionless, Power}
import scala.collection.Set
import scala.jdk.CollectionConverters._
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
    val nodeCreator = createNode(vTarget, voltageLevel) _

    osmGraph
      .vertexSet()
      .asScala
      .foldLeft(GridElements(Map.empty, Set()))((gridElements, osmNode) => {
        nodesWithBuildings.get(osmNode) match {
          case Some(buildingGraphConnection: BuildingGraphConnection) => {
            val highwayNode = nodeCreator(
              buildingGraphConnection
                .createHighwayNodeName(config.considerHouseConnectionPoints),
              osmNode.coordinate
            )
            val loadCreator = createLoad(
              "Load of building: " + buildingGraphConnection.building.id.toString,
              buildingGraphConnection.buildingPower
            ) _
            if (config.considerHouseConnectionPoints) {
              val osmBuildingConnectionNode =
                buildingGraphConnection.buildingConnectionNode.getOrElse(
                  throw IllegalStateException(
                    s"Building node for building graph connection $buildingGraphConnection has to be present when considering building connections."
                  )
                )
              val buildingConnectionNode: NodeInput = nodeCreator(
                buildingGraphConnection.createBuildingNodeName(),
                osmBuildingConnectionNode.coordinate
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
          }
          case None if osmGraph.degreeOf(osmNode) > 2 =>
            gridElements + nodeCreator(
              s"Node highway: ${osmNode.id}",
              osmNode.coordinate
            )
        }
      })
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

  def createLoad(id: String, ratedPower: ComparableQuantity[Power])(
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

  def buildLines(osmGraph: OsmGraph, gridElements: GridElements) = {
    // build line between nodes of graph
    // consider nodes with attached loads or intersections
    // other nodes are only for geoposition of line

    val startingNode = osmGraph
      .vertexSet()
      .asScala
      .headOption
      .getOrElse(
        throw MissingOsmDataException(
          s"The osm graph $osmGraph does not contain any nodes."
        )
      )

  }

  def traverseGraph(
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
    // traverse through every of the current node to build lines
    connectedEdges.foldLeft((alreadyVisited, lines)) {
      case ((updatedAlreadyVisited, updatedLines), edge) =>
        val nextNode = getOtherEdgeNode(osmGraph, currentNode, edge)
        if (!alreadyVisited.contains(nextNode)) {
          val (maybeNextNodeInput, maybeNextNode, passedStreetNodes) =
            findNextNodeInput(
              osmGraph,
              currentNode,
              edge,
              alreadyVisited,
              nodeToNodeInput
            )
          maybeNextNodeInput.zip(maybeNextNode) match {
            case Some((nextNodeInput, nextNode)) =>
              val newLine =
                buildLine(
                  currentNodeInput,
                  nextNodeInput,
                  passedStreetNodes,
                  lineTypeInput
                )
              val (visitedNodes, builtLines) = traverseGraph(
                nextNode,
                nextNodeInput,
                osmGraph,
                alreadyVisited ++ passedStreetNodes,
                lines + newLine,
                nodeToNodeInput,
                lineTypeInput
              )
              (
                updatedAlreadyVisited ++ visitedNodes,
                updatedLines ++ builtLines
              )
            case None =>
              // no further nodes to consider on this branch of the graph
              (updatedAlreadyVisited, updatedLines)
          }
        } else {
          // we've already been at this node before so we are done on this branch of the graph
          (updatedAlreadyVisited, updatedLines)
        }
    }
  }

  def buildLine(
      nodeA: NodeInput,
      nodeB: NodeInput,
      geoNodes: Seq[Node],
      lineType: LineTypeInput
  ): LineTypeInput = {
    ???
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
  def findNextNodeInput(
      graph: OsmGraph,
      currentNode: Node,
      lastEdge: DistanceWeightedEdge,
      alreadyVisited: Set[Node],
      nodeToNodeInput: Map[Node, NodeInput],
      passedNodes: Seq[Node] = Seq.empty
  ): (Option[NodeInput], Option[Node], Seq[Node]) = {
    nodeToNodeInput.get(currentNode) match {
      case Some(nodeInput) =>
        (Some(nodeInput), Some(currentNode), passedNodes :+ currentNode)
      case None =>
        graph.edgesOf(currentNode).asScala.filter(_ != lastEdge).toSeq match {
          case Seq(nextEdge) =>
            val nextNode = getOtherEdgeNode(graph, currentNode, nextEdge)
            findNextNodeInput(
              graph,
              nextNode,
              nextEdge,
              alreadyVisited + nextNode,
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
}

object GridBuildingSupport {
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
}
