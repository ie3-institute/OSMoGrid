/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import com.typesafe.scalalogging.LazyLogging
import edu.ie3.datamodel.graph.DistanceWeightedEdge
import edu.ie3.datamodel.models.input.NodeInput
import edu.ie3.datamodel.models.input.connector.LineInput
import edu.ie3.datamodel.models.input.connector.`type`.{
  LineTypeInput,
  Transformer2WTypeInput
}
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.datamodel.models.input.system.LoadInput
import edu.ie3.datamodel.models.voltagelevels.VoltageLevel
import edu.ie3.osmogrid.exception.IllegalStateException
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.osmogrid.guardian.run.RunGuardian
import edu.ie3.osmogrid.lv.LvGraphGeneratorSupport.BuildingGraphConnection
import edu.ie3.util.osm.model.OsmEntity.Node
import tech.units.indriya.ComparableQuantity
import utils.Clustering.Cluster
import utils.GridConversion.{
  buildGridContainer,
  buildLine,
  buildLoad,
  buildNode,
  buildTransformer2W
}
import utils.{Clustering, VoltageUtils}

import javax.measure.quantity.ElectricPotential
import scala.annotation.tailrec
import scala.collection.Set
import scala.collection.parallel.{ParMap, ParSeq}
import scala.jdk.CollectionConverters._

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
      substations: Map[Node, NodeInput],
      loads: Set[LoadInput]
  ) {
    def +(load: LoadInput): GridElements = {
      GridElements(this.nodes, this.substations, this.loads + load)
    }

    def ++(nodes: Map[Node, NodeInput], substations: Boolean): GridElements = {
      if (substations) {
        GridElements(this.nodes, this.substations ++ nodes, this.loads)
      } else {
        GridElements(this.nodes ++ nodes, this.substations, this.loads)
      }
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
    * @param ratedVoltageLv
    *   the rated low voltage of the grid to build
    * @param ratedVoltageMv
    *   the rated medium voltage of the grid to build
    * @param considerHouseConnectionPoints
    *   whether or not to build distinct lines to houses
    * @param loadSimultaneousFactor
    *   simultaneous factor for loads
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
      ratedVoltageLv: ComparableQuantity[ElectricPotential],
      ratedVoltageMv: ComparableQuantity[ElectricPotential],
      considerHouseConnectionPoints: Boolean,
      loadSimultaneousFactor: Double,
      lineType: LineTypeInput,
      transformer2WTypeInput: Transformer2WTypeInput,
      gridName: String
  ): List[SubGridContainer] = {
    val nodesWithBuildings: ParMap[Node, BuildingGraphConnection] =
      buildingGraphConnections.map(bgc => (bgc.graphConnectionNode, bgc)).toMap

    val voltageLevel = new VoltageLevel("lv", ratedVoltageLv)
    val nodeCreator = buildNode(voltageLevel) _

    val gridElements = osmGraph
      .vertexSet()
      .asScala
      .foldLeft(GridElements(Map(), Map(), Set()))((gridElements, osmNode) => {
        nodesWithBuildings.get(osmNode) match {
          case Some(buildingGraphConnection: BuildingGraphConnection)
              if buildingGraphConnection.isSubstation =>
            val substationNode = nodeCreator(
              "",
              osmNode.coordinate,
              false
            )
            gridElements ++ (Map(osmNode -> substationNode), true)
          case Some(buildingGraphConnection: BuildingGraphConnection) =>
            val highwayNode = nodeCreator(
              buildingGraphConnection
                .createHighwayNodeName(
                  considerHouseConnectionPoints
                ),
              osmNode.coordinate,
              false
            )
            val loadCreator = buildLoad(
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
              gridElements ++ (Map(
                osmNode -> highwayNode,
                osmBuildingConnectionNode -> buildingConnectionNode
              ), false) + load

            } else {
              val load = loadCreator(highwayNode)
              gridElements ++ (Map(osmNode -> highwayNode), false) + load
            }

          case None if osmGraph.degreeOf(osmNode) > 2 =>
            val node = nodeCreator(
              s"Highway node: ${osmNode.id}",
              osmNode.coordinate,
              false
            )
            gridElements ++ (Map(osmNode -> node), false)
          case None =>
            gridElements
        }
      })
    if (gridElements.loads.isEmpty) {
      return List.empty
    }
    val (startNode, startNodeInput) = gridElements.nodes.headOption.getOrElse(
      throw new IllegalArgumentException(
        "We have no electrical nodes to convert."
      )
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

    // todo: happens with connected graphs with size of 1
    if (unvisitedNodes.nonEmpty) {
      logger.error(
        "We did not visit all nodes while traversing the graph. Unvisited Nodes: " + unvisitedNodes
      )
    }

    clusterLvGrids(
      gridElements,
      lineInputs,
      gridName,
      loadSimultaneousFactor,
      ratedVoltageMv,
      transformer2WTypeInput
    )
  }

  def clusterLvGrids(
      gridElements: GridElements,
      lineInputs: Set[LineInput],
      gridNameBase: String,
      loadSimultaneousFactor: Double,
      ratedVoltageMv: ComparableQuantity[ElectricPotential],
      transformer2WTypeInput: Transformer2WTypeInput
  ): List[SubGridContainer] = {
    val cluster: List[Cluster] = Clustering
      .setup(
        gridElements,
        lineInputs.toSet,
        transformer2WTypeInput,
        loadSimultaneousFactor
      )
      .run
    val lineMap = lineInputs.map { l => (l.getNodeA, l.getNodeB) -> l }.toMap

    // converting the cluster into an actual psdm subgrid
    cluster.map { c =>
      val substation = c.substation
      val nodes = c.nodes.toSet + substation
      val lines: Map[(NodeInput, NodeInput), LineInput] = lineMap.filter {
        case ((nodeA, nodeB), _) =>
          nodes.contains(nodeA) && nodes.contains(nodeB)
      }

      val voltageLevel = new VoltageLevel("mv", ratedVoltageMv)

      val mvNode = buildNode(voltageLevel)(
        s"Mv node to lv node ${substation.getId}",
        substation.getGeoPosition,
        isSlack = true
      )(subnet = 100)

      val transformer2W =
        buildTransformer2W(mvNode, substation, 1, transformer2WTypeInput)

      val allNodes: Set[NodeInput] = nodes + mvNode

      buildGridContainer(
        gridNameBase,
        allNodes.asJava,
        lines.values.toSet.asJava,
        gridElements.loads.asJava
      )(transformer2Ws = Set(transformer2W).asJava)
    }
  }

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
    connectedEdges.foldLeft((alreadyVisited + currentNode, lines)) {
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

}
