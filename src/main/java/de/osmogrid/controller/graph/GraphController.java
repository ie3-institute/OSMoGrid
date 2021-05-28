/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
*/
package de.osmogrid.controller.graph;

import static de.osmogrid.util.OsmoGridUtils.GEO2QM_CORRECTION;
import static edu.ie3.util.quantities.PowerSystemUnits.*;
import static tech.units.indriya.unit.Units.METRE;

import de.osmogrid.config.OsmogridConfig;
import de.osmogrid.model.graph.DistanceWeightedOsmEdge;
import de.osmogrid.model.graph.OsmGraph;
import de.osmogrid.model.graph.OsmGridNode;
import de.osmogrid.util.OsmDataProvider;
import de.osmogrid.util.OsmoGridUtils;
import de.osmogrid.util.QuantityUtils;
import de.osmogrid.util.exceptions.EmptyClusterException;
import de.osmogrid.util.quantities.OsmoGridUnits;
import de.osmogrid.util.quantities.PowerDensity;
import edu.ie3.util.exceptions.GeoPreparationException;
import edu.ie3.util.geo.GeoUtils;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.measure.Quantity;
import javax.measure.quantity.Area;
import javax.measure.quantity.Dimensionless;
import javax.measure.quantity.Length;
import javax.measure.quantity.Power;
import math.geom2d.Vector2D;
import net.morbz.osmonaut.geometry.Polygon;
import net.morbz.osmonaut.osm.LatLon;
import net.morbz.osmonaut.osm.Node;
import net.morbz.osmonaut.osm.Tags;
import net.morbz.osmonaut.osm.Way;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.alg.connectivity.BiconnectivityInspector;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.alg.interfaces.ShortestPathAlgorithm;
import org.jgrapht.alg.shortestpath.JohnsonShortestPaths;
import org.jgrapht.graph.AsSubgraph;
import org.locationtech.jts.geom.Point;
import tech.units.indriya.ComparableQuantity;
import tech.units.indriya.quantity.Quantities;

/**
 * Controls all operations on the graph.
 *
 * @author Mahr
 * @since 17.12.2018
 */
public class GraphController {

  public static final Logger logger = LogManager.getLogger(GraphController.class);

  private static final List<AsSubgraph<OsmGridNode, DistanceWeightedOsmEdge>> graphModel =
      new ArrayList<>();
  private OsmDataProvider osmDataProvider;
  private OsmogridConfig osmogridConfig;
  private OsmGraph fullGraph;

  public void initialize(OsmogridConfig osmogridConfig) {
    this.osmogridConfig = osmogridConfig;

    osmDataProvider = new OsmDataProvider();
    osmDataProvider.initialize(
        osmogridConfig.io.pbfFilePath, osmogridConfig.runtime.cutArea, osmogridConfig.runtime.plot);

    fullGraph = osmDataProvider.getRawGraph();
  }

  /**
   * Calls all methods necessary for generating the graph model.
   *
   * @return The graph model separated into subgraphs.
   */
  public List<AsSubgraph<OsmGridNode, DistanceWeightedOsmEdge>> generateGraph() {

    ComparableQuantity<PowerDensity> powerDensity =
        Quantities.getQuantity(
            osmogridConfig.grid.averagePowerDensity, OsmoGridUnits.WATT_PER_SQUARE_METRE);

    logger.info("Calculating perpendiculars");
    calcPerpendicularDistanceMatrix(powerDensity);

    logger.info("Refreshing the graph information");
    fullGraph = refreshGraph();

    logger.info("Checking clusters for presence of buildings");
    removeEmptyLandUses();

    logger.info("Calculating and setting edge weights");
    addEdgeWeights();

    logger.info("Clearing up the graph by removing unnecessary dead ends");
    cleanGraph(fullGraph);

    logger.info("Creating clusters");
    List<Polygon> convexLandUses = new LinkedList<>();
    try {
      convexLandUses = buildConvexHulls();
    } catch (GeoPreparationException e) {
      logger.error("Could not build convex hull for landuse", e);
      System.exit(-1);
    }

    List<Set<OsmGridNode>> clusters = createClusterKMedoids(convexLandUses);

    logger.info("Creating subgraphs");
    createSubgraphs(clusters);
    logger.info("Created " + graphModel.size() + " subgraphs");

    logger.info("Cleaning up the subgraphs");
    for (AsSubgraph<OsmGridNode, DistanceWeightedOsmEdge> subgraph : graphModel) {
      cleanGraph(subgraph);
    }

    logger.info("Calculating cluster loads");
    calculateClusterLoads();

    return graphModel;
  }

  /**
   * Calculates the perpendicular point on the street for each building using the distance matrix.
   */
  private void calcPerpendicularDistanceMatrix(ComparableQuantity<PowerDensity> powerDensity) {

    List<Way> buildings = osmDataProvider.getBuildings();
    Set<Way>[][] highwayDistanceMatrix = osmDataProvider.getHighwayDistanceMatrix();

    // add highways that already have been visited and have a Perp here
    Set<Way> hasPerp = new HashSet<>();
    Map<Way, Set<OsmGridNode>> highwayPerps = new HashMap<>();

    for (Way building : buildings) {
      boolean isSubstation = osmDataProvider.getRealSubstations().contains(building);

      LatLon center = building.getCenter();
      Vector2D p = new Vector2D(center.getLon(), center.getLat());

      // now we have to check if the buildings center is inside a residential area, if yes go on, if
      // no go next
      if (GeoUtils.isInsideLanduse(center, osmDataProvider.getLandUses())) {
        Vector2D p0fin = new Vector2D();
        Way highwayfin = new Way();
        OsmGridNode n1Fin = new OsmGridNode();
        OsmGridNode n2Fin = new OsmGridNode();
        boolean aFin = false;
        boolean bFin = false;

        double distance = 1000;

        // get highways near building
        int xCoord =
            (int)
                    Math.ceil(
                        ((center.getLon() - osmDataProvider.getMinLongitude()))
                            / ((osmDataProvider.getMaxLongitude()
                                - osmDataProvider.getMinLongitude()))
                            * highwayDistanceMatrix[0].length)
                - 1;
        int yCoord =
            (int)
                    Math.ceil(
                        -((center.getLat() - osmDataProvider.getMaxLatitude()))
                            / ((osmDataProvider.getMaxLatitude()
                                - osmDataProvider.getMinLatitude()))
                            * highwayDistanceMatrix.length)
                - 1;

        Set<Way> highways = highwayDistanceMatrix[yCoord][xCoord]; // the highways in the own cell
        if (yCoord < highwayDistanceMatrix.length - 1) {
          highways.addAll(highwayDistanceMatrix[yCoord + 1][xCoord]); // the highways above
        }
        if (yCoord != 0) {
          highways.addAll(highwayDistanceMatrix[yCoord - 1][xCoord]); // the highways below
        }
        if (xCoord < highwayDistanceMatrix[0].length - 1) {
          highways.addAll(highwayDistanceMatrix[yCoord][xCoord + 1]); // the highways @right
        }
        if (xCoord != 0) {
          highways.addAll(highwayDistanceMatrix[yCoord][xCoord - 1]); // the highways @left
        }

        for (Way highway : highways) {
          for (int i = 0; i < highway.getNodes().size(); i++) {

            if (i != 0) {
              OsmGridNode n = new OsmGridNode(highway.getNodes().get(i - 1));
              OsmGridNode n1 = new OsmGridNode(highway.getNodes().get(i));

              Vector2D a = new Vector2D(n.getLatlon().getLon(), n.getLatlon().getLat());
              Vector2D b = new Vector2D(n1.getLatlon().getLon(), n1.getLatlon().getLat());
              Vector2D u =
                  new Vector2D(
                      n1.getLatlon().getLon() - n.getLatlon().getLon(),
                      n1.getLatlon().getLat() - n.getLatlon().getLat());

              // intermediate steps to calculate orthogonal projection direction vector
              double t = (p.minus(a)).dot(u); // x - r0 * u
              double t1 = u.dot(u); // u*u
              double t2 = t / t1; // [(x-r0)*u] / [u*u]

              Vector2D pd =
                  a.plus((u.times(t2)).minus(p)); // resulting orthogonal projection direction
              Vector2D p0 = p.plus(pd); // resulting orthogonal projection point on way

              double s1 = (p0.x() - a.x()) / u.x();
              double s2 = (p0.y() - a.y()) / u.y();

              // round them because only the first few numbers are interesting
              s1 = Math.round(s1 * 100000000.0) / 100000000.0;
              s2 = Math.round(s2 * 100000000.0) / 100000000.0;

              if (s1 == s2 && s1 > 0 && s1 < 1) { // lambda a to b is always 1 because of u = a - b

                // calculate length of orthogonal vector
                double l = Math.sqrt(Math.pow(pd.x(), 2) + Math.pow(pd.y(), 2));

                // get the minimal distance
                if (distance > l) {
                  distance = l;
                  p0fin = p0;
                  highwayfin = highway;
                  n1Fin = n;
                  n2Fin = n1;
                  bFin = false;
                  aFin = false;
                }
              }

              // check if distance between corners a & b is even smaller than current distance
              // calculate distance between building center and corners a & b
              double ap = Math.sqrt(Math.pow(a.minus(p).x(), 2) + Math.pow(a.minus(p).y(), 2));
              double bp = Math.sqrt(Math.pow(b.minus(p).x(), 2) + Math.pow(b.minus(p).y(), 2));

              double small =
                  Math.min(Math.min(distance, ap), bp); // get the smallest number of them

              if (distance > small && small == ap) {
                distance = small;
                p0fin = a;
                highwayfin = highway;
                n1Fin = n;
                n2Fin = n1;
                aFin = true;
                bFin = false;
              }

              if (distance > small && small == bp) {
                distance = small;
                p0fin = b;
                highwayfin = highway;
                n1Fin = n;
                n2Fin = n1;
                bFin = true;
                aFin = false;
              }

              if (small == bp && small == ap) {
                // TODO: Specify the error!
                logger.error("ERROR!");
              }
            }
          }
        }

        // check if a or b are reusable or create new node
        OsmGridNode n;
        if (aFin) {
          n = n1Fin;
        } else if (bFin) {
          n = n2Fin;
        } else {
          LatLon latLon = new LatLon(p0fin.y(), p0fin.x());
          n =
              new OsmGridNode(
                  UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE,
                  new Tags(),
                  latLon); // unique id necessary
        }

        // calc building power consumption
        double geoArea = GeoUtils.calculateBuildingArea(building);
        Quantity<Area> area = GeoUtils.calcGeo2qmNew(geoArea, GEO2QM_CORRECTION);
        Quantity<Power> load = OsmoGridUtils.calcPower(area, powerDensity);

        // check if highway has already a perp
        if (hasPerp.contains(highwayfin)) { // highway has already a perp node

          // add node to graph or modify node if existent
          if (!fullGraph.addVertex(n)) {
            long id = n.getId();
            OsmGridNode tempNode =
                fullGraph.vertexSet().stream()
                    .filter(node -> node.getId() == id)
                    .findFirst()
                    .orElseThrow();
            tempNode.setHouseConnectionPoint(building.getCenter());
            tempNode.setSubStation(isSubstation);
            if (tempNode.getLoad() != null) {
              tempNode.setLoad(tempNode.getLoad().add(load));
            } else {
              tempNode.setLoad(load);
            }
          } else {
            n.setHouseConnectionPoint(building.getCenter());
            n.setLoad(load);
            n.setSubStation(isSubstation);
          }

          // get the nodes that are on specific highway
          Set<OsmGridNode> nodes = highwayPerps.get(highwayfin);

          // extract nodes that are connected to node under investigation but are not on the
          // specific highway
          for (OsmGridNode edn : fullGraph.vertexSet()) {
            if (!fullGraph.getAllEdges(n, edn).isEmpty()) {
              nodes.add(edn);
            }
          }

          // check if vertex/node is already part of the fullGraph
          // --> it is either a perp node or a highway node
          if (fullGraph.edgesOf(n).isEmpty()) {
            // vertex/node is no perp node of highway yet
            // --> node/vertex is a new created perpNode or an existing node on highway without
            // perpnode yet
            double d1 = 1000;
            double d2 = 1000;
            OsmGridNode nd1 = n1Fin;
            OsmGridNode nd2 = n2Fin;

            // iterator through all nodes on highway and near highway to find the nearest ones
            // idea: 1. criteria: part of the straight
            // 2. criteria: smallest distance

            for (OsmGridNode node : nodes) {
              Quantity<Length> d =
                  GeoUtils.calcHaversine(
                      n.getLatlon().getLat(),
                      n.getLatlon().getLon(),
                      node.getLatlon().getLat(),
                      node.getLatlon().getLon());

              if (d.getValue().doubleValue() < d1
                  && GeoUtils.isBetween(node, nd2, n)
                  && !(node.equals(nd2))) {
                d1 = d.getValue().doubleValue();
                nd1 = node;
              }

              if (d.getValue().doubleValue() < d2
                  && GeoUtils.isBetween(node, nd1, n)
                  && !(node.equals(nd1))) {
                d2 = d.getValue().doubleValue();
                nd2 = node;
              }
            }

            fullGraph.removeEdge(nd1, nd2);

            // add new edges
            if (!nd1.equals(n)) {
              fullGraph.addEdge(nd1, n);
            }
            if (!nd2.equals(n)) {
              fullGraph.addEdge(n, nd2);
            }
          }

        } else { // highway has no perp node yet

          // remove old connection between n1 and n2
          fullGraph.removeEdge(n1Fin, n2Fin);

          // p0fin node is new vertex

          // add node to graph or modify node if existent
          if (!fullGraph.addVertex(n)) {
            long id = n.getId();
            OsmGridNode tempNode =
                fullGraph.vertexSet().stream()
                    .filter(node -> node.getId() == id)
                    .findFirst()
                    .orElseThrow();
            tempNode.setHouseConnectionPoint(building.getCenter());
            tempNode.setSubStation(isSubstation);
            if (tempNode.getLoad() != null) {
              tempNode.setLoad(tempNode.getLoad().add(load));
            } else {
              tempNode.setLoad(load);
            }
          } else {
            n.setHouseConnectionPoint(building.getCenter());
            n.setLoad(load);
            n.setSubStation(isSubstation);
          }

          // connect new vertex with new points
          if (aFin || bFin) { // node is a or b
            if (aFin) {
              fullGraph.addEdge(n, n2Fin);
            }
            if (bFin) {
              fullGraph.addEdge(n1Fin, n);
            }
          } else { // node is between a and b
            fullGraph.addEdge(n1Fin, n);
            fullGraph.addEdge(n, n2Fin);
          }
        }

        // add highwayfin to hasPerp because it has at least one
        hasPerp.add(highwayfin);

        if (highwayPerps.containsKey(highwayfin)) {
          highwayPerps.get(highwayfin).add(n);
          highwayPerps.get(highwayfin).add(n1Fin);
          highwayPerps.get(highwayfin).add(n2Fin);
        } else {
          Set<OsmGridNode> nodes = new HashSet<>();
          nodes.add(n);
          nodes.add(n1Fin);
          nodes.add(n2Fin);
          nodes.addAll(OsmoGridUtils.getOsmoGridNodeList(highwayfin.getNodes()));
          highwayPerps.put(highwayfin, nodes);
        }
      }
    }
  }

  /**
   * Calculates the perpendicular point on the street for each building using orthogonal projection.
   */
  @SuppressWarnings("unused")
  private void calcPerpendicularOrthogonalProjection(
      ComparableQuantity<PowerDensity> powerDensity) {

    List<Way> highways = osmDataProvider.getHighways();
    List<Way> buildings = osmDataProvider.getBuildings();

    logger.info("Calculating Perpendiculars...");
    long tStart = System.currentTimeMillis();

    // add highways that already has been visited and have a Perp here
    Set<Way> hasPerp = new HashSet<>();
    Map<Way, Set<OsmGridNode>> highwayPerps = new HashMap<>();

    // TODO: restrict search for highways to highways near the house to speed up calculations
    for (Way building : buildings) {
      boolean isSubstation = osmDataProvider.getRealSubstations().contains(building);

      Vector2D p = new Vector2D(building.getCenter().getLon(), building.getCenter().getLat());

      // now we have to check if the building center point is inside a residential area,
      // if yes go on, if not go next
      if (GeoUtils.isInsideLanduse(building.getCenter(), osmDataProvider.getLandUses())) {
        Vector2D p0Fin = new Vector2D();
        Way highwayFin = new Way();
        OsmGridNode n1Fin = new OsmGridNode();
        OsmGridNode n2Fin = new OsmGridNode();
        boolean aFin = false;
        boolean bFin = false;
        double distance = 1000;

        for (Way highway : highways) {

          for (int i = 1; i < highway.getNodes().size(); i++) {

            OsmGridNode n = new OsmGridNode(highway.getNodes().get(i - 1));
            OsmGridNode n1 = new OsmGridNode(highway.getNodes().get(i));

            Vector2D a = new Vector2D(n.getLatlon().getLon(), n.getLatlon().getLat());
            Vector2D b = new Vector2D(n1.getLatlon().getLon(), n1.getLatlon().getLat());

            Vector2D u =
                new Vector2D(
                    n1.getLatlon().getLon() - n.getLatlon().getLon(),
                    n1.getLatlon().getLat() - n.getLatlon().getLat());

            // intermediate steps to calculate orthogonal projection direction vector
            double t = (p.minus(a)).dot(u); // x - r0 * u
            double t1 = u.dot(u); // u*u
            double t2 = t / t1; // [(x-r0)*u] / [u*u]

            Vector2D pd =
                a.plus((u.times(t2)).minus(p)); // resulting orthogonal projection direction
            // vector r0 + [(x-r0)*u] / [u*u] - x
            Vector2D p0 = p.plus(pd); // resulting orthogonal projection point on way

            double s1 = (p0.x() - a.x()) / u.x();
            double s2 = (p0.y() - a.y()) / u.y();

            // round them because only the first few numbers are interesting
            s1 = Math.round(s1 * 100000000.0) / 100000000.0;
            s2 = Math.round(s2 * 100000000.0) / 100000000.0;

            if (s1 == s2 && s1 > 0 && s1 < 1) { // lambda a to b is always 1 because of u = a - b

              // calculate length of orthogonal vector
              double l = Math.sqrt(Math.pow(pd.x(), 2) + Math.pow(pd.y(), 2));

              // get the minimal distance
              if (distance > l) {
                distance = l;
                p0Fin = p0;
                highwayFin = highway;
                n1Fin = n;
                n2Fin = n1;
                bFin = false;
                aFin = false;
              }
            }

            // check if distance between corners a & b is even smaller than current distance
            // calculate distance between building center and corners a & b
            double ap = Math.sqrt(Math.pow(a.minus(p).x(), 2) + Math.pow(a.minus(p).y(), 2));
            double bp = Math.sqrt(Math.pow(b.minus(p).x(), 2) + Math.pow(b.minus(p).y(), 2));

            double small = Math.min(Math.min(distance, ap), bp); // get the smallest number of them

            if (distance > small && small == ap) {
              distance = small;
              p0Fin = a;
              highwayFin = highway;
              n1Fin = n;
              n2Fin = n1;
              aFin = true;
              bFin = false;
            }

            if (distance > small && small == bp) {
              distance = small;
              p0Fin = b;
              highwayFin = highway;
              n1Fin = n;
              n2Fin = n1;
              bFin = true;
              aFin = false;
            }

            if (small == bp && small == ap) {
              // TODO: Specify error
              logger.error("ERROR!");
            }
          }
        }

        // create new Node
        OsmGridNode n;
        if (aFin) {
          n = n1Fin;
        } else if (bFin) {
          n = n2Fin;
        } else {
          LatLon latLon = new LatLon(p0Fin.y(), p0Fin.x());
          n =
              new OsmGridNode(
                  UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE,
                  new Tags(),
                  latLon); // unique id necessary
        }

        // calc building power consumption
        double geoArea = GeoUtils.calculateBuildingArea(building);
        Quantity<Area> area = GeoUtils.calcGeo2qmNew(geoArea, GEO2QM_CORRECTION);
        Quantity<Power> load = OsmoGridUtils.calcPower(area, powerDensity);

        n.setHouseConnectionPoint(building.getCenter());
        n.setLoad(load);
        n.setSubStation(isSubstation);

        // check if highway has already a perp
        if (hasPerp.contains(highwayFin)) { // highway has already a perp node

          // add node to graph or modify node if existent
          if (!fullGraph.addVertex(n)) {
            long id = n.getId();
            OsmGridNode tempNode =
                fullGraph.vertexSet().stream()
                    .filter(node -> node.getId() == id)
                    .findFirst()
                    .orElseThrow();
            tempNode.setHouseConnectionPoint(building.getCenter());
            tempNode.setSubStation(isSubstation);
            if (tempNode.getLoad() != null) {
              tempNode.setLoad(tempNode.getLoad().add(load));
            } else {
              tempNode.setLoad(load);
            }
          }

          // get the nodes that are on specific highway
          Set<OsmGridNode> nodes = highwayPerps.get(highwayFin);

          // extract nodes that are connected to node under investigation but are not on the
          // specific highway
          for (OsmGridNode edn : fullGraph.vertexSet()) {
            if (!fullGraph.getAllEdges(n, edn).isEmpty()) {
              nodes.add(edn);
            }
          }

          // check if node is already part of the fullGraph --> it is either a perp node or a
          // highway node
          if (fullGraph.edgesOf(n).isEmpty()) {
            // vertex/node is no perp node of highway yet --> node/vertex is a new created perpNode
            // or
            // an existing node on highway without perpNode yet
            double d1 = 1000;
            double d2 = 1000;
            OsmGridNode nd1 = n1Fin;
            OsmGridNode nd2 = n2Fin;
            // iterator through all nodes on highway and near highway to find the nearest ones
            // idea: 1. criteria: part of the straigt
            // 2. criteria: smallest distance
            Iterator<OsmGridNode> nodesIterator = nodes.iterator();
            int nodesSize = nodes.size();
            double nodesCounter = 0;
            while (nodesIterator.hasNext()) {
              OsmGridNode nx = nodesIterator.next();
              Quantity<Length> d =
                  GeoUtils.calcHaversine(
                      n.getLatlon().getLat(),
                      n.getLatlon().getLon(),
                      nx.getLatlon().getLat(),
                      nx.getLatlon().getLon());

              if (d.getValue().doubleValue() < d1
                  && GeoUtils.isBetween(nx, nd2, n)
                  && !(nx.equals(nd2))) {
                d1 = d.getValue().doubleValue();
                nd1 = nx;
              }

              if (d.getValue().doubleValue() < d2
                  && GeoUtils.isBetween(nx, nd1, n)
                  && !(nx.equals(nd1))) {
                d2 = d.getValue().doubleValue();
                nd2 = nx;
              }
            }

            fullGraph.removeEdge(nd1, nd2);
            // add new edges
            fullGraph.addEdge(nd1, n);
            fullGraph.addEdge(n, nd2);
          }

        } else { // highway has no perp node yet
          // remove old connection between n1 and n2
          fullGraph.removeEdge(n1Fin, n2Fin);

          // p0Fin node is new vertex
          // add node to graph or modify node if existent
          if (!fullGraph.addVertex(n)) {
            long id = n.getId();
            OsmGridNode tempNode =
                fullGraph.vertexSet().stream()
                    .filter(node -> node.getId() == id)
                    .findFirst()
                    .orElseThrow();
            tempNode.setHouseConnectionPoint(building.getCenter());
            tempNode.setSubStation(isSubstation);
            if (tempNode.getLoad() != null) {
              tempNode.setLoad(tempNode.getLoad().add(load));
            } else {
              tempNode.setLoad(load);
            }
          }

          // connect new vertex with new points
          if (aFin || bFin) { // node is a or b
            if (aFin) {
              fullGraph.addEdge(n, n2Fin);
            }
            if (bFin) {
              fullGraph.addEdge(n1Fin, n);
            }
          } else { // node is between a and b
            fullGraph.addEdge(n1Fin, n);
            fullGraph.addEdge(n, n2Fin);
          }
        }

        // add highwayFin to hasPerp because it has at least one
        hasPerp.add(highwayFin);

        if (highwayPerps.containsKey(highwayFin)) {
          highwayPerps.get(highwayFin).add(n);
          highwayPerps.get(highwayFin).add(n1Fin);
          highwayPerps.get(highwayFin).add(n2Fin);
        } else {
          Set<OsmGridNode> nodes = new HashSet<>();
          nodes.add(n);
          nodes.add(n1Fin);
          nodes.add(n2Fin);
          nodes.addAll(OsmoGridUtils.getOsmoGridNodeList(highwayFin.getNodes()));
          highwayPerps.put(highwayFin, nodes);
        }
      }
    }

    long tEnd = System.currentTimeMillis();
    long tDelta = tEnd - tStart;
    double elapsedSeconds = tDelta / 1000.0;
    logger.debug("Perpendiculars calculation took " + elapsedSeconds + "seconds.");
  }

  /**
   * Refreshes all {@link DistanceWeightedOsmEdge}s. This is necessary after {@link OsmGridNode}s
   * are modified, because the modification of {@link OsmGridNode}s is not communicated to the
   * {@link DistanceWeightedOsmEdge}s.
   */
  private OsmGraph refreshGraph() {

    OsmGraph refreshedGraph = new OsmGraph();
    fullGraph.vertexSet().forEach(refreshedGraph::addVertex);

    // iterate through all edges, get the edges source and target node from the graph, remove the
    // old edge and add
    // the edge again with the correct nodes
    for (DistanceWeightedOsmEdge edge : fullGraph.edgeSet()) {
      OsmGridNode source =
          fullGraph.vertexSet().stream()
              .filter(node -> node.getId() == fullGraph.getEdgeSource(edge).getId())
              .findFirst()
              .orElse(null);
      OsmGridNode target =
          fullGraph.vertexSet().stream()
              .filter(node -> node.getId() == fullGraph.getEdgeTarget(edge).getId())
              .findFirst()
              .orElse(null);
      ComparableQuantity<Length> weight =
          Quantities.getQuantity(fullGraph.getEdgeWeight(edge), METRE);

      DistanceWeightedOsmEdge e = new DistanceWeightedOsmEdge();
      refreshedGraph.setEdgeWeight(e, weight);
      refreshedGraph.addEdge(source, target, e);
    }
    return refreshedGraph;
  }

  /** Checks for every land use whether it contains at least one building. */
  private void removeEmptyLandUses() {
    Set<Way> landUsesToBeRemoved = new HashSet<>();

    // check for the land uses
    for (Way landUse : osmDataProvider.getLandUses()) {
      boolean anyBuildingInLandUse = false;
      for (Way building : osmDataProvider.getBuildings()) {
        // check if building center is in land use
        if (GeoUtils.rayCasting(new Polygon(landUse), building.getCenter())) {
          anyBuildingInLandUse = true;
          break;
        }
      }
      if (!anyBuildingInLandUse) {
        landUsesToBeRemoved.add(landUse);
      }
    }

    // remove empty land uses
    logger.info("Found " + landUsesToBeRemoved.size() + " empty land use(s) that will be deleted.");
    osmDataProvider.getLandUses().removeAll(landUsesToBeRemoved);
  }

  /**
   * Calculates distances for all {@link DistanceWeightedOsmEdge}s and adds them as edge weights.
   */
  private void addEdgeWeights() {
    for (DistanceWeightedOsmEdge edge : fullGraph.edgeSet()) {
      OsmGridNode edgeSource = fullGraph.getEdgeSource(edge);
      OsmGridNode edgeTarget = fullGraph.getEdgeTarget(edge);
      Quantity<Length> distance =
          GeoUtils.calcHaversine(
              edgeSource.getLatlon().getLat(),
              edgeSource.getLatlon().getLon(),
              edgeTarget.getLatlon().getLat(),
              edgeTarget.getLatlon().getLon());
      fullGraph.setEdgeWeight(edge, distance.getValue().doubleValue());
    }
  }

  /** Removes dead ends without a load from the {@link Graph}. */
  private void cleanGraph(Graph<OsmGridNode, DistanceWeightedOsmEdge> graph) {

    boolean graphClean = false;
    while (!graphClean) {
      List<OsmGridNode> deadEnds =
          graph.vertexSet().stream()
              .filter(n -> graph.degreeOf(n) < 2 && n.getLoad() == null)
              .collect(Collectors.toList());
      if (deadEnds.size() == 0) {
        graphClean = true;
      } else {
        graph.removeAllVertices(deadEnds);
      }
    }
  }

  /**
   * Builds convex hulls around the land uses to also consider {@link OsmGridNode}s that are nearby
   * the land uses.
   *
   * @return A list of {@link Polygon}s representing the convex hulls.
   */
  private List<Polygon> buildConvexHulls() throws GeoPreparationException {

    List<Way> landUses = osmDataProvider.getLandUses();
    List<Polygon> convexLandUses = new LinkedList<>();

    // TODO: if the method buildConvexHull fails, we could retry with another precision value
    // TODO: adjust exception handling

    if (osmogridConfig.grid.separateClustersByLandUses) {
      // separate clusters by land uses: true
      for (Way landUse : landUses) {
        Set<Point> points = new HashSet<>();
        for (Node node : landUse.getNodes()) {
          points.add(GeoUtils.latlonToPoint(node.getLatlon()));
        }
        Polygon convexLandUse;

        try {
          convexLandUse =
              GeoUtils.buildConvexHull(
                  OsmoGridUtils.toJavaAwtPoints(points), 5, GeoUtils.ConvexHullAlgorithm.GRAHAM);
          convexLandUses.add(convexLandUse);
        } catch (GeoPreparationException e) {
          // only throw the exception if the convexLandUses is empty and the current land use was
          // the last one
          if (convexLandUses.isEmpty() && landUses.indexOf(landUse) == landUses.size() - 1) {
            throw e;
          } else {
            logger.warn(
                "Could not build the convex land use for land use " + landUses.indexOf(landUse));
          }
        }
      }
    } else {
      // separate clusters by land uses: false
      Set<Point> points = new HashSet<>();
      for (Way landUse : landUses) {
        for (Node node : landUse.getNodes()) {
          points.add(GeoUtils.latlonToPoint(node.getLatlon()));
        }
      }
      Polygon convexLandUse;
      try {
        convexLandUse =
            GeoUtils.buildConvexHull(
                OsmoGridUtils.toJavaAwtPoints(points), 5, GeoUtils.ConvexHullAlgorithm.GRAHAM);
        convexLandUses.add(convexLandUse);
      } catch (GeoPreparationException e) {
        logger.error("Could not build convex hull for landuse", e);
        System.exit(-1);
      }
    }
    return convexLandUses;
  }

  /**
   * Collects and separates all nodes that are inside one of the {@link Polygon}s.
   *
   * @param convexLandUses List of {@link Polygon}s (has to contain at least one element).
   * @return All nodes that have to be clustered separated by {@link Polygon}s.
   */
  private Set<Set<OsmGridNode>> collectClusterNodes(List<Polygon> convexLandUses) {

    // Map to check whether a node has already been assigned to a cluster set
    Map<OsmGridNode, Boolean> addedNodes = new HashMap<>();
    fullGraph.vertexSet().forEach(node -> addedNodes.put(node, false));

    Set<Set<OsmGridNode>> clusterSets = new HashSet<>();
    for (Polygon convexLandUse : convexLandUses) {
      Set<OsmGridNode> clusterNodes = new HashSet<>();
      for (OsmGridNode node : fullGraph.vertexSet()) {
        if (!addedNodes.get(node)) {
          if (node.getHouseConnectionPoint() != null) {
            if (GeoUtils.rayCasting(convexLandUse, node.getHouseConnectionPoint())) {
              clusterNodes.add(node);
              addedNodes.put(node, true);
            } else if (GeoUtils.rayCasting(convexLandUse, node.getCenter())) {
              clusterNodes.add(node);
              addedNodes.put(node, true);
            }
          } else if (GeoUtils.rayCasting(convexLandUse, node.getCenter())) {
            clusterNodes.add(node);
            addedNodes.put(node, true);
          }
        }
      }
      clusterSets.add(clusterNodes);
    }

    // each cluster set has to be fully connected (if not, split into separate cluster sets)
    List<Set<OsmGridNode>> unconnectedParts = new LinkedList<>();
    List<Set<OsmGridNode>> setsToRemove = new LinkedList<>();
    double minClusterSize = osmogridConfig.grid.ignoreClustersSmallerThan;
    int ignoredParts = 0;
    for (Set<OsmGridNode> clusterSet : clusterSets) {
      AsSubgraph<OsmGridNode, DistanceWeightedOsmEdge> tempSubgraph =
          new AsSubgraph<>(fullGraph, new HashSet<>(clusterSet));
      ConnectivityInspector<OsmGridNode, DistanceWeightedOsmEdge> conIn =
          new ConnectivityInspector<>(tempSubgraph);
      if (!conIn.isConnected()) {
        List<Set<OsmGridNode>> connectedSets = conIn.connectedSets();
        for (Set<OsmGridNode> connectedSet : connectedSets) {
          // add up load
          Quantity<Power> loadSum = Quantities.getQuantity(0.0, KILOWATT);
          for (OsmGridNode node : connectedSet) {
            if (node.getLoad() != null) {
              loadSum = loadSum.add(node.getLoad());
              if (loadSum.getValue().doubleValue() >= minClusterSize) {
                break;
              }
            }
          }
          if (loadSum.getValue().doubleValue() < minClusterSize) {
            // delete connected set
            ignoredParts++;
          } else {
            // separate connected set
            unconnectedParts.add(connectedSet);
          }
        }
        setsToRemove.add(clusterSet);
      }
    }
    logger.info(
        "Found "
            + ignoredParts
            + " unconnected part(s) that is/are too small and will be left out");
    clusterSets.removeAll(setsToRemove);
    clusterSets.addAll(unconnectedParts);

    return clusterSets;
  }

  /** Creates clusters based on the k-medoids algorithm. */
  private List<Set<OsmGridNode>> createClusterKMedoids(List<Polygon> convexLandUses) {

    Quantity<Power> loadSubstation =
        Quantities.getQuantity(osmogridConfig.grid.loadSubstation, KILOVOLTAMPERE);

    // collect cluster nodes (all nodes inside the land use/convex hull)
    Set<Set<OsmGridNode>> clusterSets = collectClusterNodes(convexLandUses);

    // calculate the number of required clusters
    Map<Set<OsmGridNode>, Quantity<Power>> landUseLoadMap = new LinkedHashMap<>();
    Map<Set<OsmGridNode>, Integer> numberOfClustersMap = new HashMap<>();
    for (Set<OsmGridNode> clusterSet : clusterSets) {
      // sum of load in the current grid area
      Quantity<Power> loadSum = Quantities.getQuantity(0.0, KILOWATT);
      for (OsmGridNode node : clusterSet) {
        if (node.getLoad() != null) {
          loadSum = loadSum.add(node.getLoad());
        }
      }
      int numberOfClusters =
          QuantityUtils.ceil(loadSum.divide(loadSubstation).asType(Dimensionless.class))
              .getValue()
              .intValue();

      numberOfClustersMap.put(clusterSet, numberOfClusters);
      landUseLoadMap.put(clusterSet, loadSum);

      logger.info(
          "Overall load of cluster set is: "
              + loadSum
              + ".\n\t\tThe maximum load allowed is: "
              + loadSubstation
              + ".\n\t\tThe number of necessary clusters is: "
              + numberOfClusters);
    }

    // k-medoids algorithm for each set of nodes
    List<Set<OsmGridNode>> clusters = new LinkedList<>();
    for (Set<OsmGridNode> clusterSet : clusterSets) {

      // get number of required clusters
      int numberOfClusters = numberOfClustersMap.get(clusterSet);

      AbstractMap.SimpleEntry<
              Graph<OsmGridNode, DistanceWeightedOsmEdge>, Map<OsmGridNode, Set<OsmGridNode>>>
          simplifiedSubgraphMap = null;

      if (numberOfClusters > 1) {
        AsSubgraph<OsmGridNode, DistanceWeightedOsmEdge> clusterSubgraph =
            new AsSubgraph<>(fullGraph, new HashSet<>(clusterSet));

        cleanGraph(clusterSubgraph);

        simplifiedSubgraphMap = simplifyGraph(clusterSubgraph);
        Graph<OsmGridNode, DistanceWeightedOsmEdge> simplifiedSubgraph =
            simplifiedSubgraphMap.getKey();

        ShortestPathAlgorithm<OsmGridNode, DistanceWeightedOsmEdge> shortestPaths =
            new JohnsonShortestPaths<>(simplifiedSubgraph);

        // start k-medoids
        KMedoidsSameSize kMedoids =
            new KMedoidsSameSize(
                shortestPaths,
                simplifiedSubgraph,
                numberOfClusters,
                landUseLoadMap.get(clusterSet).divide(numberOfClusters).getValue().doubleValue()
                    * 1.1,
                true,
                500);
        try {
          clusters.addAll(kMedoids.run());
        } catch (EmptyClusterException e) {
          logger.error("Detected at least one empty cluster three times in a row. Abort!", e);
          System.exit(-1);
        }
      } else {
        // if only one cluster is needed for this cluster set, we only have to set the cluster
        for (OsmGridNode node : clusterSet) {
          node.setCluster(0);
        }
        clusterSet.stream()
            .filter(node -> node.getLoad() != null)
            .findAny()
            .orElseThrow()
            .setSubStation(true);
        clusters.add(clusterSet);
      }

      // rebuild simplified graph
      if (simplifiedSubgraphMap != null) {
        while (!simplifiedSubgraphMap.getValue().isEmpty()) {
          Set<OsmGridNode> nodeIterator =
              clusterSet.stream()
                  .filter(node -> node.getCluster() >= 0)
                  .collect(Collectors.toSet());
          for (OsmGridNode node : nodeIterator) {
            // if the node represents other nodes in the simplified graph, the simplified nodes have
            // to be assigned to the same cluster
            if (simplifiedSubgraphMap.getValue().containsKey(node)) {
              for (OsmGridNode simplifiedNode : simplifiedSubgraphMap.getValue().get(node)) {
                // the previous added load has to be subtracted again
                if (node.getLoad() != null) {
                  if (simplifiedNode.getLoad() != null) {
                    node.setLoad(node.getLoad().subtract(simplifiedNode.getLoad()));
                  }
                  if (node.getLoad().getValue().intValue() == 0) {
                    node.setLoad(null);
                  }
                }
                simplifiedNode.setCluster(node.getCluster());
                for (Set<OsmGridNode> cluster : clusters) {
                  if (cluster.contains(node)) {
                    cluster.add(simplifiedNode);
                  }
                }
              }
              simplifiedSubgraphMap.getValue().remove(node);
            }
          }
        }
      }
    }
    // set a unique cluster id for each cluster
    for (Set<OsmGridNode> cluster : clusters) {
      for (OsmGridNode node : cluster) {
        node.setCluster(clusters.indexOf(cluster));
      }
    }
    return clusters;
  }

  /**
   * Recursive helper method for graph simplification. Has to be called at a dead end. Walks
   * recursively to the next intersection.
   *
   * @param graph The {@link Graph} that is simplified during the call of this method.
   * @param deadEnd The dead end node where the recursion starts.
   * @param lastNode The last visited {@link OsmGridNode}. Used for determining the next {@link
   *     OsmGridNode} to examine.
   * @param visitedNodes A list with all {@link OsmGridNode}s visited.
   * @param loadSum The added up load from all {@link OsmGridNode}s visited until now.
   * @return A list with all visited {@link OsmGridNode}s from the dead end to the intersection.
   */
  private List<OsmGridNode> walkDeadEnd(
      Graph<OsmGridNode, DistanceWeightedOsmEdge> graph,
      OsmGridNode deadEnd,
      OsmGridNode lastNode,
      List<OsmGridNode> visitedNodes,
      Quantity<Power> loadSum) {
    if (loadSum == null) {
      loadSum = Quantities.getQuantity(0.0, KILOWATT);
    }

    // since the passed node should at most have two neighbor nodes,
    // the one that not equals the last node is the next node
    OsmGridNode neighborNode =
        Graphs.neighborSetOf(graph, deadEnd).stream()
            .filter(node -> node != lastNode)
            .findFirst()
            .orElseThrow();

    if (graph.degreeOf(neighborNode) == 2) {
      // test whether the added up load would fit in the maximum transformer load
      if (neighborNode.getLoad() != null) {
        loadSum = loadSum.add(neighborNode.getLoad());
      }
      visitedNodes.add(0, deadEnd);
      visitedNodes = walkDeadEnd(graph, neighborNode, deadEnd, visitedNodes, loadSum);
    } else {
      // if the degree of the node unequals 2, we either reached an intersection or another dead end
      // (unlikely)
      // -> add up load, add neighbor node to visitedNodes and complete recursive method call
      visitedNodes.add(0, deadEnd);
      if (neighborNode.getLoad() != null) {
        loadSum = loadSum.add(neighborNode.getLoad());
      }
      neighborNode.setLoad(loadSum);
      visitedNodes.add(0, neighborNode);
    }
    return visitedNodes;
  }

  /**
   * Simplifies a {@link Graph} by pooling dead ends and cycles (like turning cycles) to one
   * representing node each.
   *
   * @param graph The {@link Graph} to simplify.
   * @return The simplified {@link Graph} mapped with a {@link Map}, representing the simplified
   *     nodes and their representing nodes.
   */
  private AbstractMap.SimpleEntry<
          Graph<OsmGridNode, DistanceWeightedOsmEdge>, Map<OsmGridNode, Set<OsmGridNode>>>
      simplifyGraph(Graph<OsmGridNode, DistanceWeightedOsmEdge> graph) {

    // defines the maximum transformer load, the load sum of simplified parts must not be greater
    // than this value
    double maxLoad = osmogridConfig.grid.loadSubstation;
    Graph<OsmGridNode, DistanceWeightedOsmEdge> simplifiedGraph = new AsSubgraph<>(graph);
    Map<OsmGridNode, Set<OsmGridNode>> simplifiedNodes = new HashMap<>();

    // detect dead ends and walk to the next intersection
    boolean noDeadEndsLeft = false;
    boolean noCyclesLeft = false;
    while (!noDeadEndsLeft && !noCyclesLeft) {
      // get all dead ends
      Set<OsmGridNode> deadEnds =
          simplifiedGraph.vertexSet().stream()
              .filter(node -> simplifiedGraph.degreeOf(node) == 1)
              .collect(Collectors.toSet());

      if (!deadEnds.isEmpty()) {
        noDeadEndsLeft = false;
        noCyclesLeft = false;
        for (OsmGridNode deadEnd : deadEnds) {
          // since it is possible that a single street (two dead ends) already has been simplified,
          // the
          // resulting representing node has now a degree of zero and cannot be more simplified
          if (simplifiedGraph.degreeOf(deadEnd) == 0) {
            continue;
          }

          // recursive method call, returns a list containing all nodes from the dead end (first
          // index) to the
          // next intersection (last index)
          List<OsmGridNode> visitedNodes =
              walkDeadEnd(simplifiedGraph, deadEnd, null, new LinkedList<>(), deadEnd.getLoad());

          // now the pair <intersection, all other nodes> is put in the simplifiedNodesMap
          OsmGridNode representingNode = visitedNodes.remove(0);
          if (simplifiedNodes.containsKey(representingNode)) {
            simplifiedNodes.get(representingNode).addAll(visitedNodes);
          } else {
            simplifiedNodes.put(representingNode, new HashSet<>(visitedNodes));
          }

          // remove the nodes from the simplified graph
          simplifiedGraph.removeAllVertices(visitedNodes);
        }
      } else {
        noDeadEndsLeft = true;
      }

      // get all cycles
      // we are going to simplify cycles that do have exactly one node whose degree is greater than
      // 2 (all other
      // nodes degrees are equal 2), the node whose degree is greater than 2 serves as the
      // representing node
      BiconnectivityInspector<OsmGridNode, DistanceWeightedOsmEdge> inspector =
          new BiconnectivityInspector<>(simplifiedGraph);
      Set<Graph<OsmGridNode, DistanceWeightedOsmEdge>> blocks =
          inspector.getBlocks().stream()
              .filter(g -> g.vertexSet().size() > 2)
              .collect(Collectors.toSet());

      for (Graph<OsmGridNode, DistanceWeightedOsmEdge> block : blocks) {
        int counter = 0; // counts the number of nodes with degree greater than 2
        for (OsmGridNode node : block.vertexSet()) {
          if (simplifiedGraph.degreeOf(node) > 2) {
            counter++;
          }
        }

        // only process the block/cycle if the condition above is met
        if (counter == 1) {
          Quantity<Power> loadSum = Quantities.getQuantity(0.0, KILOWATT);
          // check whether the added up load would fit the maximum transformer load
          for (OsmGridNode node : block.vertexSet()) {
            if (node.getLoad() != null) {
              loadSum = loadSum.add(node.getLoad());
            }
          }
          if (loadSum.getValue().doubleValue() < maxLoad) {
            // simplify cycle to one node (the one whose degree is greater than 2)
            OsmGridNode representingNode =
                block.vertexSet().stream()
                    .filter(node -> simplifiedGraph.degreeOf(node) > 2)
                    .findFirst()
                    .orElseThrow();

            representingNode.setLoad(loadSum);

            // add all vertices except the representing node itself to the simplified nodes
            if (simplifiedNodes.containsKey(representingNode)) {
              simplifiedNodes.get(representingNode).addAll(block.vertexSet());
              simplifiedNodes.get(representingNode).remove(representingNode);
            } else {
              simplifiedNodes.put(representingNode, new HashSet<>(block.vertexSet()));
              simplifiedNodes.get(representingNode).remove(representingNode);
            }
            // remove the nodes from the simplified graph
            simplifiedGraph.removeAllVertices(simplifiedNodes.get(representingNode));

            // there may be new dead ends to simplify again
            noDeadEndsLeft = false;
            noCyclesLeft = false;
          } else {
            noCyclesLeft = true;
          }
        }
      }
    }
    return new AbstractMap.SimpleEntry<>(simplifiedGraph, simplifiedNodes);
  }

  /** Creates a {@link AsSubgraph} for each cluster. */
  private void createSubgraphs(List<Set<OsmGridNode>> clusters) {

    // create a subgraph for each cluster set
    for (Set<OsmGridNode> cluster : clusters) {
      AsSubgraph<OsmGridNode, DistanceWeightedOsmEdge> subgraph =
          new AsSubgraph<>(fullGraph, cluster);
      ConnectivityInspector<OsmGridNode, DistanceWeightedOsmEdge> conIn =
          new ConnectivityInspector<>(subgraph);
      if (!conIn.isConnected()) {
        logger.warn("Cluster " + clusters.indexOf(cluster) + " is not fully connected.");
        logger.warn("\t\tSplit into " + conIn.connectedSets().size() + " parts.");
      }

      // check if load sum is higher than minimum cluster size from config file
      double load = 0.0;
      for (OsmGridNode node : cluster) {
        if (node.getLoad() != null) {
          load += node.getLoad().getValue().doubleValue();
        }
        if (load >= osmogridConfig.grid.ignoreClustersSmallerThan) {
          break;
        }
      }
      // add subgraph to graph model
      if (load >= osmogridConfig.grid.ignoreClustersSmallerThan) {
        graphModel.add(subgraph);
      }
    }
  }

  /** Calculates the overall load for each {@link AsSubgraph} in the graph model. */
  private void calculateClusterLoads() {
    for (AsSubgraph<OsmGridNode, DistanceWeightedOsmEdge> subgraph : graphModel) {
      double loadSum = 0.0;
      for (OsmGridNode loadNode : subgraph.vertexSet()) {
        if (loadNode.getLoad() != null) {
          loadSum += loadNode.getLoad().getValue().doubleValue();
        }
      }
      logger.info("\t\tLoad sum of cluster " + graphModel.indexOf(subgraph) + ": " + loadSum);
    }
  }
}
