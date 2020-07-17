/*
 * © 2020. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
*/
package de.osmogrid.controller.graph;

import de.osmogrid.model.graph.DistanceWeightedOsmEdge;
import de.osmogrid.model.graph.OsmGridNode;
import de.osmogrid.util.OsmDataProvider;
import de.osmogrid.util.exceptions.EmptyClusterException;
import de.osmogrid.util.exceptions.UnconnectedClusterException;
import edu.ie3.util.geo.GeoUtils;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import net.morbz.osmonaut.osm.LatLon;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.alg.interfaces.ShortestPathAlgorithm;
import org.jgrapht.graph.AsSubgraph;

/**
 * Performs a K-Medoids based algorithm and tries to generate clusters that have the same load sum.
 * Call run() to start the algorithm.
 *
 * @author Mahr
 * @since 17.12.2018
 */
public class KMedoidsSameSize {

  public static final Logger logger = LogManager.getLogger(KMedoidsSameSize.class);

  private final Graph<OsmGridNode, DistanceWeightedOsmEdge> clusterSubgraph;
  private final ShortestPathAlgorithm<OsmGridNode, DistanceWeightedOsmEdge> shortestPaths;
  private final List<OsmGridNode> clusterNodes;
  private final List<Set<OsmGridNode>> clusters;
  private final List<OsmGridNode> medoids;
  private final int k;
  private final double maxSize;
  private final boolean considerRealSubstations;
  private final int maxIterations;

  /**
   * Creates a new instance of this class.
   *
   * @param shortestPaths Instance of {@link ShortestPathAlgorithm}.
   * @param clusterSubgraph {@link Graph} that contains all nodes that have to be clustered.
   * @param k Number of desired clusters.
   * @param maxSize Maximum load sum of the clusters.
   * @param considerRealSubstations Whether to consider real substation or not.
   * @param maxIterations Number of maximum iterations. Set to -1 to iterate until no nodes are
   *     moved (Not stable yet)
   */
  KMedoidsSameSize(
      ShortestPathAlgorithm<OsmGridNode, DistanceWeightedOsmEdge> shortestPaths,
      Graph<OsmGridNode, DistanceWeightedOsmEdge> clusterSubgraph,
      int k,
      double maxSize,
      boolean considerRealSubstations,
      int maxIterations) {
    this.shortestPaths = shortestPaths;
    this.clusterSubgraph = clusterSubgraph;
    this.clusterNodes = new LinkedList<>(clusterSubgraph.vertexSet());
    this.k = k;
    this.clusters = new LinkedList<>();
    this.medoids = new LinkedList<>();
    this.maxSize = maxSize;
    this.considerRealSubstations = considerRealSubstations;
    this.maxIterations = maxIterations;
  }

  /**
   * Starts the K-Medoids algorithm
   *
   * @return Clustered nodes grouped by cluster
   * @throws EmptyClusterException If a cluster does not contain any nodes.
   */
  List<Set<OsmGridNode>> run() throws EmptyClusterException {

    logger.info(
        "Starting K-Medoids, " + k + " clusters, highest allowed cluster load: " + this.maxSize);
    logger.info("Finding initial medoids and assigning nodes ...");
    initialMedoidSelection(clusterSubgraph.vertexSet(), considerRealSubstations);
    try {
      initialAssignment();
    } catch (UnconnectedClusterException e) {
      logger.warn("At least one cluster is not fully connected", e);
    }

    logger.info("Refining results ...");
    int tries = 0;
    while (tries < 3) {
      try {
        refineResults();
        tries = 3;
      } catch (EmptyClusterException e) {
        tries++;
        if (tries == 3) {
          throw e;
        }
      }
    }

    logger.info("Updating medoids ...");
    updateMedoids();

    return clusters;
  }

  /**
   * Randomly selects initial medoids for k-medoids algorithm.
   *
   * @param nodes All {@link OsmGridNode}s that have to be clustered.
   * @param considerRealSubstations If set to true, considers previous detected real substations
   *     (from {@link OsmDataProvider}).
   */
  private void initialMedoidSelection(Set<OsmGridNode> nodes, boolean considerRealSubstations) {

    LinkedList<OsmGridNode> clusterNodes = new LinkedList<>(nodes);

    // look for real substations and add them to medoids list
    if (considerRealSubstations) {
      for (OsmGridNode node : nodes) {
        if (node.isSubStation()) {
          medoids.add(node);
        }
      }
    }

    // find medoids randomly
    for (int i = medoids.size(); i < k; i++) {
      int random = ThreadLocalRandom.current().nextInt(0, clusterNodes.size());
      medoids.add(i, clusterNodes.get(random));
    }

    // set substation value for each medoid to true
    for (OsmGridNode medoid : medoids) {
      medoid.setSubStation(true);
    }
  }

  /**
   * Randomly determines initial medoids for k-medoids algorithm (using the k-medoids++
   * initialization) (Does not work properly for all regions).
   *
   * @param nodes All {@link OsmGridNode}s that have to be clustered.
   */
  @SuppressWarnings("unused")
  private void initializeKMedoidsPlusPlus(Set<OsmGridNode> nodes) {

    // TODO: fix this method and add consideration of real substations

    LinkedList<OsmGridNode> clusterNodes = new LinkedList<>(nodes);

    // find first medoid randomly
    int random = ThreadLocalRandom.current().nextInt(0, clusterNodes.size());
    medoids.add(clusterNodes.get(random));

    // repeat k-1 times:
    for (int i = 1; i < k; i++) {

      Map<Double, OsmGridNode> squaredDistances = new HashMap<>();

      // for each node calculate the distance to each medoid and save the one to the closest medoid
      for (OsmGridNode node : clusterNodes) {
        if (!medoids.contains(node)) {
          double lowestWeight = Double.MAX_VALUE;
          for (OsmGridNode medoid : medoids) {
            // calculate distance in graph from node to medoid
            double weight = shortestPaths.getPathWeight(medoid, node);
            if (weight < lowestWeight) {
              lowestWeight = weight;
            }
          }
          squaredDistances.put(Math.pow(lowestWeight, 2.0), node);
        }
      }

      // sort and cumulate squaredDistances
      TreeMap<Double, OsmGridNode> sortedSquaredDistances = new TreeMap<>(squaredDistances);
      List<OsmGridNode> osmGridNodes = new LinkedList<>(sortedSquaredDistances.values());
      double[] cumulatedDistances = new double[sortedSquaredDistances.size()];

      int j = 0;
      for (Double distance : sortedSquaredDistances.keySet()) {
        if (j == 0) {
          cumulatedDistances[j] = distance;
        } else {
          cumulatedDistances[j] = cumulatedDistances[j - 1] + distance;
        }
        j++;
      }

      double nextMedoid = ThreadLocalRandom.current().nextDouble(0.0, cumulatedDistances[j - 1]);

      // find the least entry in cumulatedDistances that is greater than nextMedoid
      for (int l = 0; l < cumulatedDistances.length; l++) {
        if (cumulatedDistances[l] > nextMedoid) {
          medoids.add(osmGridNodes.get(l));
          break;
        }
      }
      squaredDistances.clear();
    }

    for (OsmGridNode medoid : medoids) {
      medoid.setSubStation(true);
    }
  }

  /**
   * Initializes all nodes by assigning them to their closest medoid.
   *
   * @throws UnconnectedClusterException If a cluster is not fully connected.
   */
  private void initialAssignment() throws UnconnectedClusterException {

    // initialize cluster list
    for (int i = 0; i < k; i++) {
      clusters.add(i, new HashSet<>());
    }

    // get the medoid with the shortest distance and assign to the corresponding cluster
    List<OsmGridNode> nodesToRemove = new LinkedList<>();
    for (OsmGridNode node : clusterNodes) {
      double shortestDistance = Double.POSITIVE_INFINITY;
      int cluster = -1;
      for (OsmGridNode medoid : medoids) {
        double distance = shortestPaths.getPathWeight(node, medoid);
        if (distance < shortestDistance) {
          shortestDistance = distance;
          cluster = medoids.indexOf(medoid);
        }
      }
      if (cluster < 0) {
        logger.error("Node " + node.getId() + " could not be assigned to any cluster");
        nodesToRemove.add(node);
      } else {
        node.setCluster(cluster);
        clusters.get(cluster).add(node);
      }
    }
    clusterNodes.removeAll(nodesToRemove);

    // check for each cluster whether its connected
    for (Set<OsmGridNode> cluster : clusters) {
      AsSubgraph<OsmGridNode, DistanceWeightedOsmEdge> subgraph =
          new AsSubgraph<>(clusterSubgraph, new HashSet<>(cluster));
      ConnectivityInspector<OsmGridNode, DistanceWeightedOsmEdge> connectivityInspector =
          new ConnectivityInspector<>(subgraph);
      if (connectivityInspector.connectedSets().size() > 1) {
        throw new UnconnectedClusterException(
            "Cluster " + clusters.indexOf(cluster) + " is not fully connected!");
      }
    }
  }

  /**
   * Tries to optimize the cluster allocation iteratively by moving nodes from clusters with less
   * load to clusters with higher load.
   *
   * @throws EmptyClusterException If a cluster does not contain any nodes.
   */
  private void refineResults() throws EmptyClusterException {
    // TODO: throw and handle UnconnectedClusterException here too

    int iterator = 0;

    for (int iterations = 0; maxIterations < 0 || iterations < maxIterations; iterations++) {
      // calculate load map
      Map<Integer, Double> loadMap = new LinkedHashMap<>();
      for (Set<OsmGridNode> cluster : clusters) {
        if (cluster.isEmpty()) {
          throw new EmptyClusterException("Detected empty cluster");
        }
        double loadSum = 0.0;
        for (OsmGridNode loadNode : cluster) {
          if (loadNode.getLoad() != null) {
            loadSum += loadNode.getLoad().getValue().doubleValue();
          }
        }
        loadMap.put(clusters.indexOf(cluster), loadSum);
      }
      // TODO: think on a way to terminate the algorithm (i.e. standard deviation, all cluster
      // between min/max values)

      // check movability for each node to each cluster
      Map<OsmGridNode, Boolean[]> movable = new LinkedHashMap<>(clusterNodes.size());
      List<OsmGridNode> movableNodes = new LinkedList<>();
      for (OsmGridNode node : clusterNodes) {
        double load = 0.0;
        if (node.getLoad() != null) {
          load = node.getLoad().getValue().doubleValue();
        }
        Boolean[] movableArray = new Boolean[k];
        Arrays.fill(movableArray, false);
        for (OsmGridNode neighbor : Graphs.neighborSetOf(clusterSubgraph, node)) {
          if (neighbor.getCluster() != node.getCluster()
              && loadMap.get(node.getCluster()) > loadMap.get(neighbor.getCluster()) + load) {
            movableArray[neighbor.getCluster()] = true;
            if (!movableNodes.contains(node)) {
              movableNodes.add(node);
            }
          }
        }
        movable.put(node, movableArray);
      }

      // if a node is moved, the neighbor node becomes invalid
      List<OsmGridNode> invalidNodes = new LinkedList<>();

      // Track if anything has changed
      int active = 0;

      for (OsmGridNode node : movableNodes) {
        if (invalidNodes.contains(node)) {
          continue;
        }

        int source = node.getCluster();

        for (int i = 0; i < k; i++) { // i indicates the destination cluster

          // continue with next i if node is not movable to current cluster
          if (!movable.get(node)[i]) {
            continue;
          }

          double sourceLoad = calculateLoad(source);
          double destinationLoad = calculateLoad(i);

          if (sourceLoad > destinationLoad) {
            // move node from source to destination
            transfer(source, i, node);
            active++;

            // if the node is an intersection we have to check whether the source cluster is still
            // connected
            if (clusterSubgraph.degreeOf(node) > 2) {
              // create a temporary subgraph for the source cluster and check the number of
              // connected sets
              AsSubgraph<OsmGridNode, DistanceWeightedOsmEdge> testSubgraph =
                  new AsSubgraph<>(clusterSubgraph, new HashSet<>(clusters.get(source)));

              ConnectivityInspector<OsmGridNode, DistanceWeightedOsmEdge> connectivityInspector =
                  new ConnectivityInspector<>(testSubgraph);

              if (!connectivityInspector.isConnected()) {
                // not connected: check if one of the new connected sets could be completely moved
                // to
                // the destination cluster (check if it would be an advantage too)
                List<Set<OsmGridNode>> connectedSets = connectivityInspector.connectedSets();

                // sort connected sets by size and start we the smallest
                connectedSets.sort(Comparator.comparingInt(Set::size));

                List<OsmGridNode> tempMovedNodes = new LinkedList<>();
                tempMovedNodes.add(node);

                for (Set<OsmGridNode> connectedSet : connectedSets) {

                  // calculate the load sum for the connected set
                  double loadSum = 0.0;
                  for (OsmGridNode n : connectedSet) {
                    if (n.getLoad() != null) {
                      loadSum += n.getLoad().getValue().doubleValue();
                    }
                  }
                  // check if the connected set could be completely moved to the destination cluster
                  // (check if it would be an advantage too)
                  if (destinationLoad + loadSum < maxSize
                      && destinationLoad + loadSum < sourceLoad) {
                    // move the connected set completely to destination
                    for (OsmGridNode n : connectedSet) {
                      transfer(source, i, n);
                      tempMovedNodes.add(n);
                      active++;
                    }
                    // check whether source cluster is reconnected
                    testSubgraph =
                        new AsSubgraph<>(clusterSubgraph, new HashSet<>(clusters.get(source)));
                    connectivityInspector = new ConnectivityInspector<>(testSubgraph);
                    if (connectivityInspector.isConnected()) {
                      break;
                    }
                  } else {
                    // if the cluster is still not connected and moving the unconnected parts is not
                    // possible, undo all transfers
                    for (OsmGridNode tempMovedNode : tempMovedNodes) {
                      transfer(i, source, tempMovedNode);
                      active--;
                    }
                    tempMovedNodes.clear();
                  }
                  invalidNodes.addAll(tempMovedNodes);
                }
              }
            }
            // set all neighbors of the current node invalid for transfers
            invalidNodes.addAll(Graphs.neighborSetOf(clusterSubgraph, node));
          }
          // go on with the next node
          break;
        }
      }
      if (active <= 0) {
        break;
      }

      // Clear movable node list and invalid node list for next iteration
      invalidNodes.clear();
      movableNodes.clear();
      iterator = iterations;
    }

    logger.info("Finished K-Medoids after " + (iterator + 1) + " iterations");
  }

  /**
   * Calculates the load sum for a cluster.
   *
   * @param k The cluster for which to calculate the load.
   * @return The load sum of the cluster.
   */
  private double calculateLoad(int k) {

    double loadSum = 0.0;
    for (OsmGridNode node : clusters.get(k)) {
      if (node.getLoad() != null) {
        loadSum += node.getLoad().getValue().doubleValue();
      }
    }
    return loadSum;
  }

  /**
   * Recalculates the medoid for each cluster by calculating a centroid and picking the closest
   * cluster node.
   */
  private void updateMedoids() {

    for (Set<OsmGridNode> cluster : clusters) {

      int clusterIndex = clusters.indexOf(cluster);

      // calculate the centroid of all nodes in the current cluster
      double medoidLat = 0.0;
      double medoidLon = 0.0;

      for (OsmGridNode node : cluster) {
        medoidLat += node.getLatlon().getLat();
        medoidLon += node.getLatlon().getLon();
      }

      medoidLat = (1.0 / cluster.size()) * medoidLat;
      medoidLon = (1.0 / cluster.size()) * medoidLon;
      LatLon medoidLatLon = new LatLon(medoidLat, medoidLon);

      // get the closest node in full graph
      Map<Double, OsmGridNode> distances = new LinkedHashMap<>();

      // calculate the distance from each node to the mean
      Set<OsmGridNode> loadNodes =
          cluster.stream().filter(node -> node.getLoad() != null).collect(Collectors.toSet());
      for (OsmGridNode node : loadNodes) {
        double distance =
            GeoUtils.haversine(
                    medoidLatLon.getLat(),
                    medoidLatLon.getLon(),
                    node.getLatlon().getLat(),
                    node.getLatlon().getLon())
                .getValue()
                .doubleValue();
        distances.put(distance, node);
      }

      // sort by distance and choose the node with the smallest distance
      List<Double> sortedDistances =
          distances.keySet().stream()
              .sorted(Comparator.naturalOrder())
              .collect(Collectors.toList());

      // set isSubstation from old medoid to false
      medoids.get(clusterIndex).setSubStation(false);
      // replace medoid
      medoids.set(clusterIndex, distances.get(sortedDistances.get(0)));
      // set isSubstation from new medoid to true
      medoids.get(clusterIndex).setSubStation(true);
    }
  }

  /**
   * Moves a node to another cluster.
   *
   * @param source The cluster the node currently belongs to.
   * @param destination The cluster the node has to be moved to.
   * @param node The node that has to be moved.
   */
  private void transfer(int source, int destination, OsmGridNode node) {

    if (clusters.get(source).contains(node)) {
      clusters.get(source).remove(node);
      clusters.get(destination).add(node);
      node.setCluster(destination);
    } else {
      logger.error(
          "Could not remove node "
              + node.getId()
              + " from cluster "
              + source
              + ". That should never happen!");
    }
  }
}
