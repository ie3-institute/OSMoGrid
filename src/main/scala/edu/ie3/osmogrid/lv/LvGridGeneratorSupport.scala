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
    val initialClusters: List[Cluster] = Clustering
      .setup(
        gridElements,
        lineInputs.toSet,
        transformer2WTypeInput,
        loadSimultaneousFactor,
      )
      .run

    // Replace iterative reassignment with a single, deterministic
    // connected-component based assignment. For every connected component of
    // the LV-line graph we determine which substations (if any) lie inside the
    // component. If exactly one substation is present, all nodes of the
    // component belong to that substation's cluster. If multiple substations
    // are present, we run a multi-source BFS from these substations (limited
    // to the component) and assign each node to the nearest substation. This
    // yields a deterministic, one-pass reassignment and avoids the previous
    // trial-and-error loop with mutable guards.
    val lineNeighbors: Map[NodeWrapper, Set[NodeWrapper]] = lineInputs
      .flatMap { line =>
        val a = NodeWrapper(line.getNodeA)
        val b = NodeWrapper(line.getNodeB)
        Set(a -> b, b -> a)
      }
      .groupMap(_._1)(_._2)

    val substationToIdx: Map[NodeWrapper, Int] =
      initialClusters.zipWithIndex.map { case (cluster, idx) =>
        NodeWrapper(cluster.substation.input) -> idx
      }.toMap

    val allNodes: Set[NodeWrapper] =
      (initialClusters.flatMap(_.nodes) ++ initialClusters.map(
        _.substation
      )).toSet

    // discover connected components in the full adjacency graph
    val visited = scala.collection.mutable.Set.empty[NodeWrapper]
    val components =
      scala.collection.mutable.ArrayBuffer.empty[Set[NodeWrapper]]
    val queueCtor = () => scala.collection.mutable.Queue.empty[NodeWrapper]

    allNodes.foreach { n =>
      if (!visited.contains(n)) {
        val comp = scala.collection.mutable.Set.empty[NodeWrapper]
        val q = queueCtor()
        q.enqueue(n)
        visited += n
        comp += n
        while (q.nonEmpty) {
          val cur = q.dequeue()
          lineNeighbors.getOrElse(cur, Set.empty).foreach { nbr =>
            if (allNodes.contains(nbr) && !visited.contains(nbr)) {
              visited += nbr
              q.enqueue(nbr)
              comp += nbr
            }
          }
        }
        components += comp.toSet
      }
    }

    // initial assignment as fallback
    val initialAssignment: Map[NodeWrapper, Int] =
      initialClusters.zipWithIndex.flatMap { case (cluster, idx) =>
        cluster.nodes.map(_ -> idx)
      }.toMap

    val finalAssignment =
      scala.collection.mutable.Map.empty[NodeWrapper, Int] ++ initialAssignment

    components.foreach { comp =>
      // substations present in this component (as cluster indices)
      val subs: Set[Int] = comp.flatMap(n => substationToIdx.get(n))

      if (subs.size == 1) {
        // single substation -> assign all nodes to that cluster
        val idx = subs.head
        comp.foreach(n => finalAssignment.update(n, idx))
      } else if (subs.size > 1) {
        // multi-source BFS: determine nearest substation for each node
        val sources: Seq[NodeWrapper] =
          comp.filter(substationToIdx.contains).toSeq

        if (sources.nonEmpty) {
          val dist =
            scala.collection.mutable.Map.empty[NodeWrapper, (NodeWrapper, Int)]
          val q = queueCtor()
          sources.foreach { s =>
            dist.update(s, (s, 0)); q.enqueue(s)
          }

          while (q.nonEmpty) {
            val cur = q.dequeue()
            val (src, d) = dist(cur)
            lineNeighbors.getOrElse(cur, Set.empty).foreach { nbr =>
              if (comp.contains(nbr) && !dist.contains(nbr)) {
                dist.update(nbr, (src, d + 1))
                q.enqueue(nbr)
              }
            }
          }

          // assign nodes to nearest source's cluster
          dist.foreach { case (node, (source, _)) =>
            substationToIdx.get(source).foreach { idx =>
              finalAssignment.update(node, idx)
            }
          }
        }
      }
    }

    val clusters: List[Cluster] = initialClusters.zipWithIndex
      .map { case (cluster, idx) =>
        // ensure we provide an immutable Set[NodeWrapper]
        cluster.copy(nodes = finalAssignment.filter(_._2 == idx).keySet.toSet)
      }
      .filter(_.nodes.nonEmpty)

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
      val allNodesInSubgrid = lvNodes ++ Set(NodeWrapper(mvNode))

      buildGridContainer(
        s"LV-subnet-$subnetNr",
        allNodesInSubgrid.map(_.input).asJava,
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
