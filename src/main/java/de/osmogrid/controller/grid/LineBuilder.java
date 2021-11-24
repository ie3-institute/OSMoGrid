/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package de.osmogrid.controller.grid;

import de.osmogrid.model.graph.DistanceWeightedOsmEdge;
import de.osmogrid.model.graph.OsmGridNode;
import de.osmogrid.util.OsmoGridUtils;
import edu.ie3.datamodel.models.input.NodeInput;
import edu.ie3.datamodel.models.input.connector.LineInput;
import edu.ie3.datamodel.models.input.connector.type.LineTypeInput;
import edu.ie3.util.geo.GeoUtils;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.measure.quantity.Length;
import org.jgrapht.graph.AsSubgraph;
import tech.units.indriya.ComparableQuantity;

/**
 * Provides the methods, based on depth first search, to build the {@link LineInput}s of the
 * electrical grid from the graph. A special algorithm is necessary because not all {@link
 * OsmGridNode}s from the graph are connected with one {@link LineInput} but only those with a load
 * (or intersections).
 *
 * @author Mahr
 * @since 17.12.2018
 */
public class LineBuilder {

  private AsSubgraph<OsmGridNode, DistanceWeightedOsmEdge> subgraph;
  private final LineTypeInput lineTypeInput;
  private Map<OsmGridNode, NodeInput> geoGridNodesMap;
  private OsmGridNode startNode;
  private OsmGridNode endNode;
  private List<OsmGridNode> geoNodes;
  private Map<OsmGridNode, VisitColor> nodeColorMap;
  private OsmGridNode lastVisitedNode;
  private int lineIdCounter;
  private List<LineInput> lineInputModels;

  protected enum VisitColor {
    WHITE, // Vertex has not been visited yet.
    GRAY, // Vertex has been visited, but we're not done with all of its edges yet.
    BLACK // Vertex has been visited and we're done with all of its edges.
  }

  public LineBuilder(LineTypeInput lineTypeInput) {
    this.lineTypeInput = lineTypeInput;
  }

  public void initialize(
      AsSubgraph<OsmGridNode, DistanceWeightedOsmEdge> subgraph,
      Map<OsmGridNode, NodeInput> geoGridNodesMap,
      int lineIdCounter) {
    this.subgraph = subgraph;
    this.geoGridNodesMap = geoGridNodesMap;
    this.startNode = null;
    this.endNode = null;
    this.geoNodes = new LinkedList<>();
    this.nodeColorMap = new HashMap<>();
    this.lineIdCounter = lineIdCounter;
    this.lineInputModels = new LinkedList<>();

    // set the visit color for all nodes to white
    this.subgraph.vertexSet().forEach(node -> nodeColorMap.put(node, VisitColor.WHITE));
  }

  /** Starts the algorithm by looking for a node with a load. */
  public void start() {
    boolean startVertexFound = false;

    for (OsmGridNode node : subgraph.vertexSet()) {
      if (!startVertexFound && (node.getLoad() != null || subgraph.degreeOf(node) > 2))
        startVertexFound = true;
      if (startVertexFound && nodeColorMap.get(node).equals(VisitColor.WHITE)) visitNode(node);
    }
  }

  /**
   * Recursive method for visiting nodes based on the depth first search algorithm.
   *
   * @param node Node that has to be visited.
   */
  private void visitNode(OsmGridNode node) {
    nodeColorMap.remove(node);
    nodeColorMap.put(node, VisitColor.GRAY);

    boolean isDeadEnd = false;

    // investigate the current node
    if (node.getLoad() != null || subgraph.degreeOf(node) > 2) {
      // node has a load or is an intersection
      if (startNode == null) {
        // startNode not yet set
        startNode = node;
        geoNodes.add(0, startNode);
      } else {
        // startNode already set -> line complete, build line
        endNode = node;
        geoNodes.add(endNode);
        buildLineInputModel();

        // reset values
        geoNodes.clear();
        if (subgraph.degreeOf(node) < 2) {
          // if a dead end is reached, reset the start node
          startNode = null;
          isDeadEnd = true;
        } else {
          // otherwise set the start node to the current node
          startNode = node;
          geoNodes.add(startNode);
        }
      }
    } else {
      // node has neither a load nor is an intersection -> it's only going to be used to display
      // lines more detailed
      if (subgraph.degreeOf(node) < 2) {
        // unless the node is a dead end
        geoNodes.clear();
        startNode = null;
        isDeadEnd = true;
      } else {
        // node is not a dead end
        geoNodes.add(node);
      }
    }

    if (!isDeadEnd) {

      // create neighbor list (consider only "forward" nodes)
      List<OsmGridNode> neighborNodes = new LinkedList<>();
      for (DistanceWeightedOsmEdge connectedEdge : subgraph.edgesOf(node)) {
        if (subgraph.getEdgeSource(connectedEdge).equals(node)
            && !subgraph.getEdgeTarget(connectedEdge).equals(lastVisitedNode))
          neighborNodes.add(subgraph.getEdgeTarget(connectedEdge));
        else if (subgraph.getEdgeTarget(connectedEdge).equals(node)
            && !subgraph.getEdgeSource(connectedEdge).equals(lastVisitedNode))
          neighborNodes.add(subgraph.getEdgeSource(connectedEdge));
      }

      // investigate the neighbor nodes
      for (OsmGridNode neighborNode : neighborNodes) {
        // set startNode again if it's null (this could happen if we have reached a dead end or
        // closed a circle in the step before)
        if (startNode == null && (node.getLoad() != null || subgraph.degreeOf(node) > 2)) {
          startNode = node;
          geoNodes.clear();
          geoNodes.add(startNode);
        }

        if (!nodeColorMap.get(neighborNode).equals(VisitColor.WHITE)
            && subgraph.containsEdge(node, neighborNode)) {
          // if(subgraph.degreeOf(neighborNode) > 2 &&
          // nodeColorMap.get(neighborNode).equals(VisitColor.GRAY) && subgraph.containsEdge(node,
          // neighborNode)) {
          // Node is an intersection and has already been visited but is not finished yet -> finish
          // the line
          endNode = neighborNode;
          geoNodes.add(endNode);
          buildLineInputModel();

          // reset all values
          geoNodes.clear();
          startNode = null;
        } else if (nodeColorMap.get(neighborNode).equals(VisitColor.WHITE)) {
          lastVisitedNode = node;
          visitNode(neighborNode);
        }
      }
    }
    nodeColorMap.remove(node);
    nodeColorMap.put(node, VisitColor.BLACK);
  }

  /** Builds the LineInputModel when a line is complete (start and end node set). */
  private void buildLineInputModel() {
    if (startNode != null && endNode != null && startNode != endNode) {
      ComparableQuantity<Length> length =
          GeoUtils.calcHaversine(
              startNode.getLatlon().getLat(),
              startNode.getLatlon().getLon(),
              endNode.getLatlon().getLat(),
              endNode.getLatlon().getLon()); // TODO: calculate length correctly
      LineInput lineInput =
          new LineInput(
              UUID.randomUUID(),
              "Line " + lineIdCounter,
              geoGridNodesMap.get(startNode),
              geoGridNodesMap.get(endNode),
              1,
              lineTypeInput,
              length,
              OsmoGridUtils.nodesToLineString(geoNodes),
              null);

      if (lineInput.getNodeA() != null
          && lineInput.getNodeB() != null
          && lineInput.getNodeA() != lineInput.getNodeB()) {
        lineInputModels.add(lineInput);
        lineIdCounter++;
      }
    }
  }

  List<LineInput> getLineInputModels() {
    return lineInputModels;
  }

  public int getLineIdCounter() {
    return lineIdCounter;
  }

  public void setLineIdCounter(int lineIdCounter) {
    this.lineIdCounter = lineIdCounter;
  }
}
