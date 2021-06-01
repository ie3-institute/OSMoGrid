/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
*/
package de.osmogrid.util;

import de.osmogrid.model.graph.OsmGridNode;
import de.osmogrid.util.quantities.OsmoGridUnits;
import de.osmogrid.util.quantities.PowerDensity;
import edu.ie3.datamodel.models.input.NodeInput;
import edu.ie3.util.OneToOneMap;
import edu.ie3.util.quantities.PowerSystemUnits;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.measure.Quantity;
import javax.measure.quantity.Area;
import javax.measure.quantity.Power;
import net.morbz.osmonaut.osm.LatLon;
import net.morbz.osmonaut.osm.Node;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import tech.units.indriya.quantity.Quantities;
import tech.units.indriya.unit.Units;

public class OsmoGridUtils {
  /** area correction factor set to 0 (see calGeo2qm for details) */
  public static final Quantity<Area> GEO2QM_CORRECTION =
      Quantities.getQuantity(0.0, Units.SQUARE_METRE);

  /**
   * Converts a list of Nodes to a list of OsmogridNodes
   *
   * @param nodes List of Nodes which shall be converted to OsmogridNodes
   * @return List of converted OsmogridNodes
   */
  public static List<OsmGridNode> getOsmoGridNodeList(List<Node> nodes) {
    List<OsmGridNode> osmGridNodes = new LinkedList<>();
    for (Node node : nodes) {
      OsmGridNode osmGridNode = new OsmGridNode(node);
      osmGridNodes.add(osmGridNode);
    }
    return osmGridNodes;
  }

  /**
   * Map geom points to java awt points. Can be removed when GeoUtils in PSU is fixed and tested.
   *
   * @param points
   * @return
   */
  public static Set<java.awt.Point> toJavaAwtPoints(Set<Point> points) {
    return points.stream().map(OsmoGridUtils::toJavaAwtPoint).collect(Collectors.toSet());
  }

  /**
   * Map geom points to java awt points. Can be removed when GeoUtils in PSU is fixed and tested.
   *
   * @param point
   * @return
   */
  public static java.awt.Point toJavaAwtPoint(org.locationtech.jts.geom.Point point) {
    return new java.awt.Point((int) point.getX(), (int) point.getY());
  }

  /**
   * Calculates the power value of a household load based on the provided building area and the
   * provided average power density value and the provided average household area size
   *
   * @param area
   * @param density
   */
  public static Quantity<Power> calcPower(Quantity<Area> area, Quantity<PowerDensity> density) {
    Quantity<Power> power =
        area.to(Units.SQUARE_METRE)
            .multiply(density.to(OsmoGridUnits.WATT_PER_SQUARE_METRE))
            .asType(Power.class)
            .to(PowerSystemUnits.KILOWATT);

    return QuantityUtils.ceil(power).asType(Power.class);
  }

  /**
   * Calculates the geo position as a {@link LineString} from a given collection of {@link
   * OsmGridNode}s.
   *
   * @param nodes Node list from which the geo position shall be calculated.
   * @return Calculated LineString from the given node list.
   */
  public static LineString nodesToLineString(Collection<OsmGridNode> nodes) {

    Set<LatLon> latLons = nodes.stream().map(Node::getLatlon).collect(Collectors.toSet());
    return latLonsToLineString(latLons);
  }

  /**
   * Calculates the geo position as a {@link LineString} from a given collection of {@link LatLon}s.
   *
   * @param latLons LatLon list from which the geo position shall be calculated.
   * @return Calculated LineString from the given LatLon list.
   */
  public static LineString latLonsToLineString(Collection<LatLon> latLons) {
    org.locationtech.jts.geom.GeometryFactory geometryFactory =
        new org.locationtech.jts.geom.GeometryFactory();
    LineString geoPosition;

    if (latLons.size() >= 2) {
      org.locationtech.jts.geom.Coordinate[] coordinates =
          new org.locationtech.jts.geom.Coordinate[latLons.size()];
      int cnt = 0;
      for (LatLon latLon : latLons) {
        coordinates[cnt++] =
            new org.locationtech.jts.geom.Coordinate(latLon.getLon(), latLon.getLat());
      }
      geoPosition = geometryFactory.createLineString(coordinates);
      geoPosition.setSRID(4326); // Use WGS84 reference system
    } else {
      // If there are less than two geo coordinates, set geo position to null
      geoPosition = null;
    }

    return geoPosition;
  }

  public static OneToOneMap<String, Integer> buildNodeCodeMap(Collection<NodeInput> nodes) {
    OneToOneMap<String, Integer> nodeCodeMap = new OneToOneMap<>(nodes.size());
    int counter = 0;

    for (NodeInput node : nodes) {
      nodeCodeMap.put(node.getId(), counter);
      counter++;
    }
    return nodeCodeMap;
  }
}
