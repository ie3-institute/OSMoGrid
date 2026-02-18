/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import com.typesafe.scalalogging.LazyLogging
import edu.ie3.datamodel.models.input.NodeInput
import edu.ie3.datamodel.models.input.connector.LineInput
import edu.ie3.datamodel.models.input.connector.`type`.{
  LineTypeInput,
  Transformer2WTypeInput,
}
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.datamodel.models.input.system.LoadInput
import edu.ie3.datamodel.models.voltagelevels.VoltageLevel
import edu.ie3.osmogrid.exception.IllegalStateException
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.osmogrid.lv.LvGraphGeneratorSupport.BuildingGraphConnection
import edu.ie3.util.osm.model.OsmEntity.Node
import edu.ie3.util.quantities.QuantityUtils.*
import utils.Clustering
import utils.Clustering.{Cluster, NodeWrapper}
import utils.GridConversion.*

import scala.collection.Set
import scala.collection.parallel.{ParMap, ParSeq}
import scala.jdk.CollectionConverters.*

object LvGridGeneratorSupport extends LazyLogging {

  /** Container to store built grid assets.
    *
    * @param nodes
    *   mapping from osm to electrical node
    * @param loads
    *   the built loads
    */
  final case class GridElements(
      nodes: Map[Node, NodeInput] = Map.empty,
      substations: Map[Node, NodeInput] = Map.empty,
      loads: Set[LoadInput] = Set.empty,
  ) {

    def withSubstation(node: Node, nodeInput: NodeInput): GridElements =
      copy(substations = substations.updated(node, nodeInput))

    def withNode(node: Node, nodeInput: NodeInput): GridElements =
      copy(nodes = nodes.updated(node, nodeInput))

    def withLoad(load: LoadInput): GridElements =
      copy(loads = loads ++ Seq(load))

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
    * @param lvVoltage
    *   the rated low voltage of the grid to build
    * @param mvVoltage
    *   the rated medium voltage of the grid to build
    * @param considerHouseConnectionPoints
    *   whether to build distinct lines to houses
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
      lvVoltage: VoltageLevel,
      mvVoltage: VoltageLevel,
      considerHouseConnectionPoints: Boolean,
      loadSimultaneousFactor: Double,
      lineType: LineTypeInput,
      transformer2WTypeInput: Transformer2WTypeInput,
      gridName: String,
  ): Seq[SubGridContainer] = {
    val nodesWithBuildings: ParMap[Node, BuildingGraphConnection] =
      buildingGraphConnections.map(bgc => (bgc.graphConnectionNode, bgc)).toMap

    val nodeCreator = buildNode(lvVoltage)

    val gridElements = osmGraph
      .vertexSet()
      .asScala
      .foldLeft(GridElements())((gridElements, osmNode) => {
        nodesWithBuildings.get(osmNode) match {
          case Some(buildingGraphConnection: BuildingGraphConnection)
              if buildingGraphConnection.isSubstation =>
            val substationNode = nodeCreator(
              "",
              osmNode.coordinate,
              false,
            )
            gridElements.withSubstation(osmNode, substationNode)
          case Some(buildingGraphConnection: BuildingGraphConnection) =>
            val highwayNode = nodeCreator(
              buildingGraphConnection
                .createHighwayNodeName(
                  considerHouseConnectionPoints
                ),
              osmNode.coordinate,
              false,
            )
            val loadCreator = buildLoad(
              "Load of building: " + buildingGraphConnection.building.entity.id.toString,
              buildingGraphConnection.buildingPower,
            )
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
                false,
              )
              val load = loadCreator(buildingConnectionNode)
              gridElements
                .withNode(osmNode, highwayNode)
                .withNode(osmBuildingConnectionNode, buildingConnectionNode)
                .withLoad(load)

            } else {
              val load = loadCreator(highwayNode)
              gridElements.withNode(osmNode, highwayNode).withLoad(load)
            }

          case None if osmGraph.degreeOf(osmNode) > 2 =>
            val node = nodeCreator(
              s"Highway node: ${osmNode.id}",
              osmNode.coordinate,
              false,
            )
            gridElements.withNode(osmNode, node)
          case None =>
            gridElements
        }
      })
    if (gridElements.loads.isEmpty) {
      logger.debug("Skipping grid with no loads!")
      return Seq.empty
    } else if (gridElements.substations.size + gridElements.nodes.size < 2) {
      logger.debug("Skipping grid with less than two nodes in total!")
      return Seq.empty
    }

    val nodeToNodeInput = gridElements.nodes ++ gridElements.substations
    val reducedGraph = reduceGraph(osmGraph, nodeToNodeInput.keySet)

    val lineInputs = reducedGraph.edgeSet().asScala.map { edge =>
      val source = reducedGraph.getEdgeSource(edge)
      val target = reducedGraph.getEdgeTarget(edge)
      val nodeA = nodeToNodeInput(source)
      val nodeB = nodeToNodeInput(target)

      buildLine(
        s"Line between: ${nodeA.getId}-${nodeB.getId}",
        nodeA,
        nodeB,
        1,
        lineType,
        edge.getDistance,
      )
    }

    clusterLvGrids(
      gridElements,
      lineInputs,
      gridName,
      loadSimultaneousFactor,
      mvVoltage,
      transformer2WTypeInput,
    )
  }

  /** Method for clustering lv grids.
    *
    * @param gridElements
    *   elements containing [[LoadInput]]s and [[NodeInput]]s
    * @param lineInputs
    *   set of [[LineInput]]s
    * @param gridNameBase
    *   name of the grid
    * @param loadSimultaneousFactor
    *   simultaneous factor for loads
    * @param mvVoltage
    *   the rated medium voltage of the grid to build
    * @param transformer2WTypeInput
    *   type used for two winding transformers
    * @return
    */
  private def clusterLvGrids(
      gridElements: GridElements,
      lineInputs: Set[LineInput],
      gridNameBase: String,
      loadSimultaneousFactor: Double,
      mvVoltage: VoltageLevel,
      transformer2WTypeInput: Transformer2WTypeInput,
  ): List[SubGridContainer] = {
    val clusters: List[Cluster] = Clustering
      .setup(
        gridElements,
        lineInputs.toSet,
        transformer2WTypeInput,
        loadSimultaneousFactor,
      )
      .run
    val lineMap = lineInputs.map { l =>
      (NodeWrapper(l.getNodeA), NodeWrapper(l.getNodeB)) -> l
    }.toMap

    // converting the cluster into an actual psdm subgrid
    clusters.zipWithIndex.map { case (cluster, idx) =>
      val subnetNr = idx + 1
      val updatedNodes =
        cluster.nodes.map(n =>
          NodeWrapper(n.input.copy().subnet(subnetNr).build())
        )

      val substationNodeB =
        NodeWrapper(cluster.substation.input.copy().subnet(subnetNr).build())

      val lvNodes = updatedNodes + substationNodeB
      val uuidToUpdatedNode =
        lvNodes.map(n => n.input.getUuid -> n.input).toMap

      val updatedLvLines = lineInputs.collect {
        case line
            if uuidToUpdatedNode.contains(line.getNodeA.getUuid) &&
              uuidToUpdatedNode.contains(line.getNodeB.getUuid) =>
          val newA = uuidToUpdatedNode(line.getNodeA.getUuid)
          val newB = uuidToUpdatedNode(line.getNodeB.getUuid)
          line.copy().nodeA(newA).nodeB(newB).build()
      }

      val updatedLoads = gridElements.loads.collect {
        case load if uuidToUpdatedNode.contains(load.getNode.getUuid) =>
          val newN = uuidToUpdatedNode(load.getNode.getUuid)
          load.copy().node(newN).build()
      }

      val mvNode = buildNode(mvVoltage)(
        s"Mv node for LV subnet $subnetNr (${substationNodeB.input.getId})",
        substationNodeB.input.getGeoPosition,
        isSlack = true,
      )(using subnet = 100)

      val transformer2W = buildTransformer2W(
        mvNode,
        substationNodeB.input,
        parallelDevices = 1,
        transformer2WTypeInput,
      )
      val allNodes = lvNodes ++ Set(NodeWrapper(mvNode))

      buildGridContainer(
        s"LV-subnet-$subnetNr",
        allNodes.map(_.input).asJava,
        updatedLvLines.toSet.asJava,
        updatedLoads.asJava,
      )(using subnetNr = subnetNr, transformer2Ws = Set(transformer2W).asJava)
    }
  }

  /** This method will reduce the graph by removing some vertices. A vertex is
    * removed if its degree is <= 2, and it is not defined to be kept.
    *
    * @param osmGraph
    *   graph to reduce
    * @param keep
    *   all [[Node]]s that should be kept
    * @return
    */
  private def reduceGraph(
      osmGraph: OsmGraph,
      keep: Set[Node],
  ): OsmGraph = {
    osmGraph
      .vertexSet()
      .asScala
      .diff(keep)
      .foldLeft(osmGraph) { case (graph, currentNode) =>
        if (graph.degreeOf(currentNode) <= 2) {
          // the current node can be removed
          val edges = graph.edgesOf(currentNode).asScala

          if (edges.size != 1) {
            edges.headOption.zip(edges.lastOption).foreach {
              case (edgeA, edgeB) =>
                val source = graph.getOtherEdgeNode(currentNode, edgeA)
                val target = graph.getOtherEdgeNode(currentNode, edgeB)

                if (source != target) {
                  val distance = edgeA.getDistance
                    .add(edgeB.getDistance)
                    .getValue
                    .doubleValue()
                    .asMetre

                  graph.addWeightedEdge(source, target, distance)
                }
            }
          }

          graph.removeVertex(currentNode)
        }

        graph
      }
  }
}
