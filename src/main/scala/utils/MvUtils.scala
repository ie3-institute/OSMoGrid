/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package utils

import edu.ie3.datamodel.models.input.NodeInput
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.util.osm.model.OsmEntity.Node
import org.slf4j.{Logger, LoggerFactory}
import utils.Connections.buildUniqueConnections
import utils.GridConversion.NodeConversion
import utils.OsmoGridUtils.getAllUniqueCombinations
import utils.VoronoiUtils.VoronoiPolygon

import scala.jdk.CollectionConverters.CollectionHasAsScala

/** Utility object for mv generation.
  */
object MvUtils {
  val log: Logger = LoggerFactory.getLogger(MvUtils.getClass)

  /** Utility method for creating [[NodeConversion]] and [[Connections]]
    * utilities. <p> NOTICE: for correct operation -> number of vertexes >=
    * number of [[NodeInput]]s
    * @param nodes
    *   all psdm nodes
    * @param streetGraph
    *   graph with osm nodes and connections
    * @return
    *   utility objects
    */
  private def createDefinitions(
      nodes: List[NodeInput],
      streetGraph: OsmGraph
  ): (NodeConversion, Connections[Node]) = {
    val allOsmNodes: List[Node] = streetGraph.vertexSet().asScala.toList

    // building node conversion
    val nodeConversion: NodeConversion = NodeConversion(nodes, allOsmNodes)
    val osmNodes: List[Node] = nodeConversion.conversionToOsm.values.toList

    // finding all unique connections and building connections
    val uniqueConnections = getAllUniqueCombinations(osmNodes)
    val connections: Connections[Node] = Connections(
      osmNodes,
      buildUniqueConnections(uniqueConnections, streetGraph)
    )

    (nodeConversion, connections)
  }

  /** Method for generating a mv graph structure.
    *
    * @param nr
    *   subnet number
    * @param voronoiPolygon
    *   polygon with nodes
    * @param streetGraph
    *   complete osm street graph
    * @return
    *   a osm graph and the used node conversion object
    */
  def generateMvGraph(
      nr: Int,
      voronoiPolygon: VoronoiPolygon,
      streetGraph: OsmGraph
  ): (OsmGraph, NodeConversion) = {
    log.debug(s"Start graph generation for grid $nr.")

    // if this voronoi polygon contains a polygon, we can reduce the complete street graph in order to reduce the calculation time
    val reducedStreetGraph: OsmGraph = voronoiPolygon.polygon
      .map { polygon => streetGraph.subgraph(polygon) }
      .getOrElse(streetGraph)

    // creating necessary utility objects
    val (nodeConversion, connections) =
      MvUtils.createDefinitions(voronoiPolygon.allNodes, reducedStreetGraph)

    val transitionNode: Node = nodeConversion.getOsmNode(
      voronoiPolygon.transitionPointToHigherVoltLvl
    )

    // using the solver to solve the routing problem
    val graph: OsmGraph = Solver.solve(
      transitionNode,
      connections
    )

    (graph, nodeConversion)
  }
}
