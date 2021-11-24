/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package de.osmogrid.util;

import de.osmogrid.model.graph.DistanceWeightedOsmEdge;
import de.osmogrid.model.graph.OsmGraph;
import de.osmogrid.model.graph.OsmGridNode;
import edu.ie3.util.geo.GeoUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.measure.quantity.Length;
import net.morbz.osmonaut.EntityFilter;
import net.morbz.osmonaut.Osmonaut;
import net.morbz.osmonaut.osm.LatLon;
import net.morbz.osmonaut.osm.Way;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.units.indriya.ComparableQuantity;

/**
 * Provides reading and filtering for OSM input data and building a graph from this data.
 *
 * @author Mahr
 * @since 17.12.2018
 */
public class OsmDataProvider {

  public static final Logger logger = LogManager.getLogger(OsmDataProvider.class);

  private List<OsmGridNode> nodes;
  private List<Way> ways;
  private final List<Way> buildings = new ArrayList<>();
  private final List<Way> landUses = new ArrayList<>();
  private final List<Way> highways = new ArrayList<>();
  private final List<Way> realSubstations = new ArrayList<>();
  private Set<Way>[][] highwayDistanceMatrix;
  private double maxLongitude;
  private double minLongitude;
  private double maxLatitude;
  private double minLatitude;

  private static final OsmGraph rawGraph = new OsmGraph();

  public void initialize(String pbfFilePath, boolean cutArea, boolean plot) {
    logger.debug("Read OSM-Data ...");

    // Set which OSM entities should be scanned (only nodes this case)
    EntityFilter filter = new EntityFilter(true, true, false);

    // The scanned entities will be stored here
    OsmReader osmReader = new OsmReader();

    // Set the binary OSM source (pbf) file and scan for nodes and ways
    Osmonaut osmonaut = new Osmonaut(pbfFilePath, filter);
    osmonaut.scan(osmReader);
    this.nodes = OsmoGridUtils.getOsmoGridNodeList(osmReader.getNodes());
    this.ways = osmReader.getWays();

    logger.debug(this.nodes.size() + " nodes have been found.");
    logger.debug(this.ways.size() + " ways have been found.");

    // cut draw area to residential part if cutArea is true
    if (cutArea && plot) {
      maxLatitude = -1000;
      minLatitude = 1000;
      maxLongitude = -1000;
      minLongitude = 1000;

      for (Way way : this.ways) {
        if (way.getTags().hasKey("building")) {
          for (OsmGridNode node : OsmoGridUtils.getOsmoGridNodeList(way.getNodes())) {
            if (node.getLatlon().getLat() > maxLatitude) {
              maxLatitude = node.getLatlon().getLat();
            }
            if (node.getLatlon().getLat() < minLatitude) {
              minLatitude = node.getLatlon().getLat();
            }
            if (node.getLatlon().getLon() > maxLongitude) {
              maxLongitude = node.getLatlon().getLon();
            }
            if (node.getLatlon().getLon() < minLongitude) {
              minLongitude = node.getLatlon().getLon();
            }
          }
        }
      }

    } else {
      // extract max/min lat and long
      maxLatitude = -1000;
      minLatitude = 1000;
      maxLongitude = -1000;
      minLongitude = 1000;

      for (OsmGridNode node : this.nodes) {
        if (node.getLatlon().getLat() > maxLatitude) {
          maxLatitude = node.getLatlon().getLat();
        }
        if (node.getLatlon().getLat() < minLatitude) {
          minLatitude = node.getLatlon().getLat();
        }
        if (node.getLatlon().getLon() > maxLongitude) {
          maxLongitude = node.getLatlon().getLon();
        }
        if (node.getLatlon().getLon() < minLongitude) {
          minLongitude = node.getLatlon().getLon();
        }
      }
    }

    logger.debug("Extract buildings, land uses and highways ...");
    extractBuildings();
    extractLandUses();
    extractHighways();

    logger.debug(this.buildings.size() + " buildings have been found");
    logger.debug(this.landUses.size() + " residential landuses have been found");
    logger.debug(this.highways.size() + " highways have been found");

    logger.debug("Build up raw graph ...");
    createRawGraph();

    logger.debug("Create highway distance matrix ...");
    createHighwayDistanceMatrix();
  }

  /**
   * Extracts buildings and real sub stations from ways with specified building values (tags from
   * OSM).
   */
  private void extractBuildings() {
    for (Way way : this.ways) {
      if (way.getTags().hasKey("building")) {
        if (way.getTags().hasKeyValue("building", "transformer_tower")
            || way.getTags().hasKeyValue("power", "sub_station")) {
          this.realSubstations.add(way);
        }
        this.buildings.add(way);
      }
    }
  }

  /** Extracts land uses from ways with specified land use values (tags from OSM). */
  private void extractLandUses() {
    // Array with all land use values that have to be considered
    String[] landuseValues = {
      "residential", "commercial", "retail", /*"greenfield", "meadow",*/ "farmyard"
    };
    for (String landUseValue : landuseValues) {
      for (Way way : this.ways) {
        if (way.getTags().hasKeyValue("landuse", landUseValue)) {
          this.landUses.add(way);
        }
      }
    }
  }

  /** Extracts highways from ways with specified highway values (tags from OSM). */
  private void extractHighways() {
    // Array with all highway values that have to be considered
    String[] highwayValues = {
      "residential",
      "unclassified",
      "secondary",
      "tertiary",
      "living_street",
      "footway",
      "path",
      "primary",
      "service",
      "cycleway",
      "proposed",
      "bus_stop",
      "steps",
      "track",
      "traffic_signals",
      "turning_cycle"
    };

    for (String highwayValue : highwayValues) {
      for (Way way : this.ways) {
        if (way.getTags().hasKeyValue("highway", highwayValue)) this.highways.add(way);
      }
    }
  }

  /** Builds up the rawGraph from the extracted highway nodes. */
  private void createRawGraph() {

    for (Way way : this.highways) {
      List<OsmGridNode> osmGridNodes = OsmoGridUtils.getOsmoGridNodeList(way.getNodes());

      for (int i = 1; i < osmGridNodes.size(); i++) {
        OsmGridNode n1 = osmGridNodes.get(i - 1);
        OsmGridNode n2 = osmGridNodes.get(i);

        rawGraph.addVertex(n1);
        rawGraph.addVertex(n2);

        // calculate edge weight
        ComparableQuantity<Length> weight =
            GeoUtils.calcHaversine(
                n1.getLatlon().getLat(),
                n1.getLatlon().getLon(),
                n2.getLatlon().getLat(),
                n2.getLatlon().getLon());

        // create edge and add edge to rawGraph
        DistanceWeightedOsmEdge e = new DistanceWeightedOsmEdge();
        rawGraph.setEdgeWeight(e, weight.getValue().doubleValue());
        rawGraph.addEdge(n1, n2, e); // TODO: consider checking boolean from this method
      }
    }
  }

  /** Creates an highway distance matrix. */
  @SuppressWarnings("unchecked")
  private void createHighwayDistanceMatrix() {

    // TODO: calculate or pass max/min laditude/longitude (vorher: Berechnung in
    // GeoPreparator.readOSM)

    double minCellDistance = 0.0005; // min distance between two cells in lat/lon

    // calculate number of cells
    int columns = (int) Math.ceil((maxLongitude - minLongitude) / minCellDistance);
    int rows = (int) Math.ceil((maxLatitude - minLatitude) / minCellDistance);

    // create distance matrix
    highwayDistanceMatrix = new HashSet[rows][columns];

    // add highways to fields
    for (Way way : this.highways) {
      List<OsmGridNode> nodes = OsmoGridUtils.getOsmoGridNodeList(way.getNodes());

      for (OsmGridNode n : nodes) {
        int xCoord =
            (int)
                    Math.ceil(
                        ((n.getLatlon().getLon() - minLongitude))
                            / ((maxLongitude - minLongitude))
                            * columns)
                - 1;
        int yCoord =
            (int)
                    Math.ceil(
                        -((n.getLatlon().getLat() - maxLatitude))
                            / ((maxLatitude - minLatitude))
                            * rows)
                - 1;

        if (xCoord < 0 || xCoord > columns - 1 || yCoord < 0 || yCoord > rows - 1) {
          // TODO: add exception ("at least one coordinate out of range")
        } else {
          if (highwayDistanceMatrix[yCoord][xCoord] == null)
            highwayDistanceMatrix[yCoord][xCoord] = new HashSet<>();

          highwayDistanceMatrix[yCoord][xCoord].add(way);
        }
      }
    }

    // fill null values with neighbor values
    boolean nullFound = true;
    while (nullFound) {
      nullFound = false;
      boolean[][] filled =
          new boolean[highwayDistanceMatrix.length][highwayDistanceMatrix[0].length];

      for (int i = 0; i < highwayDistanceMatrix.length; i++) {
        for (int j = 0; j < highwayDistanceMatrix[0].length; j++) {
          if (highwayDistanceMatrix[i][j] == null || highwayDistanceMatrix[i][j].isEmpty()) {
            nullFound = true;

            // look to neighbor, copy over
            highwayDistanceMatrix[i][j] = new HashSet<>();
            if (i > 0) {
              if (j > 0 && highwayDistanceMatrix[i - 1][j - 1] != null && !filled[i - 1][j - 1]) {
                highwayDistanceMatrix[i][j].addAll(highwayDistanceMatrix[i - 1][j - 1]);
                filled[i - 1][j - 1] = true;
              }
              if (j < highwayDistanceMatrix[i].length - 1
                  && highwayDistanceMatrix[i - 1][j + 1] != null
                  && !filled[i - 1][j + 1]) {
                highwayDistanceMatrix[i][j].addAll(highwayDistanceMatrix[i - 1][j + 1]);
                filled[i - 1][j + 1] = true;
              }
            }
            if (i < highwayDistanceMatrix.length - 1) {
              if (j > 0 && highwayDistanceMatrix[i + 1][j - 1] != null & !filled[i + 1][j - 1]) {
                highwayDistanceMatrix[i][j].addAll(highwayDistanceMatrix[i + 1][j - 1]);
                filled[i + 1][j - 1] = true;
              }
              if (j < highwayDistanceMatrix[i].length - 1
                  && highwayDistanceMatrix[i + 1][j + 1] != null
                  && !filled[i + 1][j + 1]) {
                highwayDistanceMatrix[i][j].addAll(highwayDistanceMatrix[i + 1][j + 1]);
                filled[i + 1][j + 1] = true;
              }
            }
          }
        }
      }
    }

    LatLon center = buildings.get(5).getCenter();

    // determine the grid point
    int xCoord =
        (int)
                Math.ceil(
                    ((center.getLon() - minLongitude)) / ((maxLongitude - minLongitude)) * columns)
            - 1;
    int yCoord =
        (int) Math.ceil(-((center.getLat() - maxLatitude)) / ((maxLatitude - minLatitude)) * rows)
            - 1;

    Set<Way> highways = highwayDistanceMatrix[yCoord][xCoord];

    if (yCoord < highwayDistanceMatrix.length - 1)
      highways.addAll(highwayDistanceMatrix[yCoord + 1][xCoord]); // the highways above
    if (yCoord != 0)
      highways.addAll(highwayDistanceMatrix[yCoord - 1][xCoord]); // the highways below
    if (xCoord < highwayDistanceMatrix[0].length - 1)
      highways.addAll(highwayDistanceMatrix[yCoord][xCoord + 1]); // the highways at right
    if (xCoord != 0)
      highways.addAll(highwayDistanceMatrix[yCoord][xCoord - 1]); // the highways at left
  }

  public List<OsmGridNode> getNodes() {
    return nodes;
  }

  public List<Way> getBuildings() {
    return buildings;
  }

  public List<Way> getWays() {
    return ways;
  }

  public List<Way> getHighways() {
    return highways;
  }

  public List<Way> getLandUses() {
    return landUses;
  }

  public List<Way> getRealSubstations() {
    return realSubstations;
  }

  public double getMaxLongitude() {
    return maxLongitude;
  }

  public double getMinLongitude() {
    return minLongitude;
  }

  public double getMaxLatitude() {
    return maxLatitude;
  }

  public double getMinLatitude() {
    return minLatitude;
  }

  public Set<Way>[][] getHighwayDistanceMatrix() {
    return highwayDistanceMatrix;
  }

  public OsmGraph getRawGraph() {
    return rawGraph;
  }
}
