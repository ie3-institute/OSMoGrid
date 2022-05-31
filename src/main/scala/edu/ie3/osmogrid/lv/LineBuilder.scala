/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import edu.ie3.osmogrid.graph.DistanceWeightedOsmEdge
import edu.ie3.osmogrid.graph.OsmGridNode
import de.osmogrid.util.OsmoGridUtils
import edu.ie3.datamodel.graph.DistanceWeightedEdge
import edu.ie3.datamodel.models.input.NodeInput
import edu.ie3.util.geo.GeoUtils

import edu.ie3.datamodel.models.input.connector
import edu.ie3.datamodel.models.input.connector.LineInput
import edu.ie3.datamodel.models.input.connector.`type`
import edu.ie3.datamodel.models.input.connector.`type`.LineTypeInput
import edu.ie3.datamodel.utils.GridAndGeoUtils.buildSafeLineStringBetweenNodes

import collection.JavaConverters.{asScalaBufferConverter, asScalaSetConverter}
import scala.jdk.CollectionConverters
import java.util
import java.util.UUID
import javax.measure.quantity.Length
import org.jgrapht.graph.AsSubgraph
import org.locationtech.jts.geom.LineString
import tech.units.indriya.ComparableQuantity

/** Provides the methods, based on depth first search, to build the {@link
  * LineInput}s of the electrical grid from the graph. A special algorithm is
  * necessary because not all {@link OsmGridNode}s from the graph are connected
  * with one {@link LineInput} but only those with a load (or intersections).
  *
  * @author
  *   Mahr
  * @since 17.12.2018
  */

/** @WHITE
  *   Vertex has not been visited yet.
  * @GRAY
  *   Vertex has been visited, but we're not done with all of its edges yet.
  * @BLACK
  *   Vertex has been visited and we're done with all of its edges
  */
object VisitColor extends Enumeration {
  type VisitColor = Value
  val WHITE, GRAY, BLACK = Value
}

import VisitColor._

class LineBuilder(
    lineTypeInput: LineTypeInput
) {
  var subgraph: AsSubgraph[OsmGridNode, DistanceWeightedEdge]
  var nodeColorMap: util.HashMap[OsmGridNode, VisitColor]
  var startNode: OsmGridNode
  var endNode: OsmGridNode
  var lastVisitedNode: OsmGridNode
  var geoNodes: util.LinkedList[OsmGridNode]
  var lineIdCounter = 0
  var lineInputModels: util.LinkedList[LineInput]
  var geoGridNodesMap: util.HashMap[OsmGridNode, NodeInput]

  def initialize(
      subgraph: AsSubgraph[OsmGridNode, DistanceWeightedEdge],
      geoGridNodesMap: util.HashMap[OsmGridNode, NodeInput],
      lineIdCounter: Int
  ): Unit = {
    this.geoGridNodesMap = geoGridNodesMap
    this.startNode = null
    this.endNode = null
    this.geoNodes = new util.LinkedList[OsmGridNode]
    val nodeColorMap = new util.HashMap[OsmGridNode, VisitColor]
    this.lineIdCounter = lineIdCounter
    this.lineInputModels = new util.LinkedList[LineInput]
    // set the visit color for all nodes to white
    subgraph.vertexSet.forEach((node: OsmGridNode) =>
      nodeColorMap.put(node, VisitColor.WHITE)
    )

  }

  /** Starts the algorithm by looking for a node with a load. */
  def start(): Unit = {
    var startVertexFound = false
    for (node <- subgraph.vertexSet().asScala) {
      if (
        !startVertexFound && (node.getLoad != null || subgraph.degreeOf(
          node
        ) > 2)
      )
        startVertexFound = true
        if (
          startVertexFound && nodeColorMap.get(
            node
          ) == VisitColor.WHITE
        ) visitNode(node)
    }
  }

  /** Recursive method for visiting nodes based on the depth first search
    * algorithm.
    *
    * @param node
    *   Node that has to be visited.
    */

  def visitNode(node: OsmGridNode): Unit = {
    nodeColorMap.remove(node)
    nodeColorMap.put(node, VisitColor.GRAY)
    var isDeadEnd = false

    // investigate the current node
    if (node.getLoad != null || subgraph.degreeOf(node) > 2) { // node has a load or is an intersection
      if (startNode == null) { // startNode not yet set
        startNode = node
        geoNodes.add(0, startNode)
      } else { // startNode already set -> line complete, build line
        endNode = node
        geoNodes.add(endNode)
        buildLineInputModel()
        // reset values
        geoNodes.clear()
        if (subgraph.degreeOf(node) < 2) { // if a dead end is reached, reset the start node
          startNode = null
          isDeadEnd = true
        } else { // otherwise set the start node to the current node
          startNode = node
          geoNodes.add(startNode)
        }
      }
    } else { // node has neither a load nor is an intersection -> it's only going to be used to display
      // lines more detailed
      if (subgraph.degreeOf(node) < 2) { // unless the node is a dead end
        geoNodes.clear()
        startNode = null
        isDeadEnd = true
      } else { // node is not a dead end
        geoNodes.add(node)
      }
    }
    if (!isDeadEnd) { // create neighbor list (consider only "forward" nodes)
      val neighborNodes = new util.LinkedList[OsmGridNode]
      for (connectedEdge <- subgraph.edgesOf(node).asScala) {
        if (
          subgraph.getEdgeSource(connectedEdge).equals(node) && !subgraph
            .getEdgeTarget(connectedEdge)
            .equals(lastVisitedNode)
        ) neighborNodes.add(subgraph.getEdgeTarget(connectedEdge))
        else if (
          subgraph.getEdgeTarget(connectedEdge).equals(node) && !subgraph
            .getEdgeSource(connectedEdge)
            .equals(lastVisitedNode)
        ) neighborNodes.add(subgraph.getEdgeSource(connectedEdge))
      }
      // investigate the neighbor nodes
      for (neighborNode <- asScalaBufferConverter(neighborNodes).asScala) { // set startNode again if it's null (this could happen if we have reached a dead end or
        // closed a circle in the step before)
        if (
          startNode == null && (node.getLoad != null || subgraph.degreeOf(
            node
          ) > 2)
        ) {
          startNode = node
          geoNodes.clear()
          geoNodes.add(startNode)
        }
        if (
          !(nodeColorMap.get(
            neighborNode
          ) == VisitColor.WHITE) && subgraph.containsEdge(
            node,
            neighborNode
          )
        ) { // if(subgraph.degreeOf(neighborNode) > 2 &&
          // nodeColorMap.get(neighborNode).equals(VisitColor.GRAY) && subgraph.containsEdge(node,
          // neighborNode)) {
          // Node is an intersection and has already been visited but is not finished yet -> finish
          // the line
          endNode = neighborNode
          geoNodes.add(endNode)
          buildLineInputModel()
          // reset all values
          geoNodes.clear()
          startNode = null
        } else if (nodeColorMap.get(neighborNode) == VisitColor.WHITE) {
          lastVisitedNode = node
          visitNode(neighborNode)
        }
      }
    }
    nodeColorMap.remove(node)
    nodeColorMap.put(node, VisitColor.BLACK)

  }

  /** Builds the LineInputModel when a line is complete (start and end node
    * set).
    */

  def buildLineInputModel() = {
    if (startNode != null && endNode != null && (startNode ne endNode)) {
      val length = GeoUtils.calcHaversine(
        startNode.getLatlon.getLat,
        startNode.getLatlon.getLon,
        endNode.getLatlon.getLat,
        endNode.getLatlon.getLon
      ) // TODO: calculate length correctly
      val lineInput = new LineInput(
        UUID.randomUUID,
        "Line " + lineIdCounter,
        geoGridNodesMap.get(startNode),
        geoGridNodesMap.get(endNode),
        1,
        lineTypeInput,
        length,
        buildSafeLineStringBetweenNodes(
          geoGridNodesMap.get(startNode),
          geoGridNodesMap.get(endNode)
        ),
        null
      )
      if (
        lineInput.getNodeA != null && lineInput.getNodeB != null && (lineInput.getNodeA ne lineInput.getNodeB)
      ) {
        lineInputModels.add(lineInput)
        lineIdCounter += 1
      }
    }
  }

  def getLineInputModels = lineInputModels

  def getLineIdCounter: Int = lineIdCounter

  def setLineIdCounter(lineIdCounter: Int): Unit = {
    this.lineIdCounter = lineIdCounter
  }
}