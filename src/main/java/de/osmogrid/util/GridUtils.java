/*
 * © 2019. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package de.osmogrid.util;

import static edu.ie3.util.geo.GeoUtils.EARTH_RADIUS;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import de.osmogrid.model.graph.OsmogridNode;
import edu.ie3.datamodel.models.input.NodeInput;
import edu.ie3.datamodel.models.input.connector.LineInput;
import edu.ie3.util.OneToOneMap;
import edu.ie3.util.quantities.PowerSystemUnits;
import edu.ie3.util.quantities.interfaces.PowerDensity;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.measure.Quantity;
import javax.measure.quantity.Angle;
import javax.measure.quantity.Area;
import javax.measure.quantity.Dimensionless;
import javax.measure.quantity.ElectricConductance;
import javax.measure.quantity.ElectricResistance;
import javax.measure.quantity.Length;
import javax.measure.quantity.Power;
import net.morbz.osmonaut.geometry.Polygon;
import net.morbz.osmonaut.osm.LatLon;
import net.morbz.osmonaut.osm.Node;
import net.morbz.osmonaut.osm.Way;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.linear.FieldMatrix;
import org.apache.commons.math3.linear.SparseFieldMatrix;
import org.jetbrains.annotations.NotNull;
import org.locationtech.jts.geom.LineString;
import tec.uom.se.ComparableQuantity;
import tec.uom.se.quantity.Quantities;
import tec.uom.se.unit.Units;

/**
 * Utility class that provides utils for geo and grid operations.
 *
 * <p>TODO: Extend {@link edu.ie3.datamodel.utils.GridAndGeoUtils}, as soon as
 * https://github.com/ie3-institute/PowerSystemDataModel/issues/135 addressed.
 *
 * @author Mahr
 * @since 17.12.2018
 */
public class GridUtils {

  /** area correction factor set to 0 (see calGeo2qm for details) */
  public static final Quantity<Area> GEO2QM_CORRECTION =
      Quantities.getQuantity(0.0, Units.SQUARE_METRE);

  /**
   * Checks if Node c is between Node a and b
   *
   * @param a Node A
   * @param b Node B
   * @param c Node C
   * @return True if node c is between node a and node b
   */
  public static boolean isBetween(Node a, Node b, Node c) {
    double crossProduct;
    double dotProduct;
    double squaredLengthBA;
    double epsilon = 0.000000000001; // epsilon to check if a,b and c are aligned
    // TODO: as the size of epsilon is crucial for the functionality, it should be available as an
    // option
    // lon = x
    // lat = y

    crossProduct =
        ((c.getLatlon().getLat() - a.getLatlon().getLat())
                * (b.getLatlon().getLon() - a.getLatlon().getLon())
            - (c.getLatlon().getLon() - a.getLatlon().getLon())
                * (b.getLatlon().getLat() - a.getLatlon().getLat()));
    if (Math.abs(crossProduct) > epsilon) {
      return false;
    }

    dotProduct =
        (c.getLatlon().getLon() - a.getLatlon().getLon())
                * (b.getLatlon().getLon() - a.getLatlon().getLon())
            + (c.getLatlon().getLat() - a.getLatlon().getLat())
                * (b.getLatlon().getLat() - a.getLatlon().getLat());

    if (dotProduct < 0) {
      return false;
    }

    squaredLengthBA =
        (b.getLatlon().getLon() - a.getLatlon().getLon())
                * (b.getLatlon().getLon() - a.getLatlon().getLon())
            + (b.getLatlon().getLat() - a.getLatlon().getLat())
                * (b.getLatlon().getLat() - a.getLatlon().getLat());

    return !(dotProduct > squaredLengthBA);

    // Commented out since the statement in the brackets is always false.
    // TODO: unnecessary if statement?
    //    if (dotProduct > squaredLengthBA && Math.abs(crossProduct) > epsilon) {
    //      return false;
    //    }
  }

  /** Calculates the distance in km between two lat/long points using the haversine formula */
  public static ComparableQuantity<Length> haversine(
      double lat1, double lng1, double lat2, double lng2) {
    Quantity<Angle> dLat = Quantities.getQuantity(Math.toRadians(lat2 - lat1), Units.RADIAN);
    Quantity<Angle> dLon = Quantities.getQuantity(Math.toRadians(lng2 - lng1), Units.RADIAN);

    double a =
        Math.sin(dLat.getValue().doubleValue() / 2) * Math.sin(dLat.getValue().doubleValue() / 2)
            + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon.getValue().doubleValue() / 2)
                * Math.sin(dLon.getValue().doubleValue() / 2);
    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

    return EARTH_RADIUS.multiply(c);
  }

  /**
   * Calculates the area of a polygon in geo coordinates to an area in square kilometers NOTE: It
   * may be possible, that (compared to the real building area), the size of the building area may
   * be overestimated. To take this into account an optional correction factor might be used.
   *
   * @param geoArea: the area of the building based on geo coordinates
   * @param cor: the optional correction factor
   */
  public static Quantity<Area> calcGeo2qm(double geoArea, Quantity<Area> cor) {
    double width = 51.5;
    double length = 7.401;

    double square = Math.sqrt(geoArea);
    double width2 = (width + square) / 180 * Math.PI;
    double length2 = length / 180 * Math.PI;
    double width3 = width / 180 * Math.PI;
    double length3 = (length + square) / 180 * Math.PI;
    width = width / 180 * Math.PI;
    length = length / 180 * Math.PI;

    double e1 =
        Math.acos(
                Math.sin(width) * Math.sin(width2)
                    + Math.cos(width) * Math.cos(width2) * Math.cos(length2 - length))
            * EARTH_RADIUS.getValue().doubleValue();
    double e2 =
        Math.acos(
                Math.sin(width) * Math.sin(width3)
                    + Math.cos(width) * Math.cos(width3) * Math.cos(length3 - length))
            * EARTH_RADIUS.getValue().doubleValue();

    // (e1 * e2) - cor
    return Quantities.getQuantity(e1, Units.METRE)
        .multiply(Quantities.getQuantity(e2, Units.METRE))
        .asType(Area.class)
        .subtract(cor);
  }

  public static boolean isInsideLanduse(LatLon node, @NotNull List<Way> landUses) {
    for (Way landUse : landUses) {
      if (rayCasting(new Polygon(landUse), node)) {
        return true;
      }
    }
    return false;
  }

  public static boolean rayCasting(@NotNull Polygon shape, LatLon node) {
    boolean inside = false;

    // get lat lon from shape nodes
    List<LatLon> shapeNodes = shape.getCoords();

    for (int i = 1; i < shapeNodes.size(); i++) {
      if (intersects(shapeNodes.get(i - 1), shapeNodes.get((i)), node)) {
        inside = !inside;
      }
    }
    return inside;
  }

  private static boolean intersects(@NotNull LatLon a, @NotNull LatLon b, @NotNull LatLon n) {

    // convert LatLons to arrays
    double[] A = {a.getLon(), a.getLat()};
    double[] B = {b.getLon(), b.getLat()};
    double[] P = {n.getLon(), n.getLat()};

    if (A[1] > B[1]) {
      return intersects(b, a, n);
    }

    if (P[1] == A[1] || P[1] == B[1]) {
      P[1] += 0.0001;
    }

    if (P[1] > B[1] || P[1] < A[1] || P[0] > Math.max(A[0], B[0])) {
      return false;
    }

    if (P[0] < Math.min(A[0], B[0])) {
      return true;
    }

    double red = (P[1] - A[1]) / (P[0] - A[0]);
    double blue = (B[1] - A[1]) / (B[0] - A[0]);
    return red >= blue;
  }

  /**
   * The algorithm assumes the usual mathematical convention that positive y points upwards. In
   * computer systems where positive y is downwards (most of them) the easiest thing to do is list
   * the vertices counter-clockwise using the "positive y down" coordinates. The two effects then
   * cancel out to produce a positive area.
   *
   * @param building
   * @return polygon area A
   */
  public static double calculateBuildingArea(@NotNull Way building) {
    double area = 0.0;

    // extract number of polygon points
    int j =
        building.getNodes().size()
            - 1; // -1 because the last vertex is the 'previous' one to the first
    // x = long
    // y = lat
    for (int i = 0; i < building.getNodes().size(); i++) {
      area =
          area
              + ((building.getNodes().get(j).getLatlon().getLon()
                      + building.getNodes().get(i).getLatlon().getLon())
                  * (building.getNodes().get(j).getLatlon().getLat()
                      - building.getNodes().get(i).getLatlon().getLat()));
      j = i;
    }

    area = Math.abs(area);

    return area / 2;
  }

  /**
   * Calculates the power value of a household load based on the provided building area and the
   * provided average power density value and the provided average household area size
   *
   * @param area
   * @param density
   */
  public static Quantity<Power> calcPower(
      @NotNull Quantity<Area> area, @NotNull Quantity<PowerDensity> density) {
    Quantity<Power> power =
        area.to(Units.SQUARE_METRE)
            .multiply(density.to(PowerSystemUnits.WATT_PER_SQUAREMETRE))
            .asType(Power.class)
            .to(PowerSystemUnits.KILOWATT);

    return QuantityUtils.ceil(power).asType(Power.class);
  }

  public static OneToOneMap<String, Integer> buildNodeCodeMap(
      @NotNull Collection<NodeInput> nodes) {
    OneToOneMap<String, Integer> nodeCodeMap = new OneToOneMap<>(nodes.size());
    int counter = 0;

    for (NodeInput node : nodes) {
      nodeCodeMap.put(node.getId(), counter);
      counter++;
    }
    return nodeCodeMap;
  }

  /**
   * Calculates the admittance matrix for the load flow calculation
   *
   * @param nodeCodeMap OneToOneMap that maps all LineInputModels with an ongoing number (starting
   *     from zero)
   * @param lines List of all LineInputModels from the sub net
   * @param sNom Nominal power of the sub net
   * @return The calculated admittance matrix
   */
  public static FieldMatrix<Complex> calcAdmittanceMatrix(
      @NotNull OneToOneMap<String, Integer> nodeCodeMap,
      @NotNull Collection<LineInput> lines,
      Quantity<Power> sNom) {

    FieldMatrix<Complex> admittanceMatrix =
        new SparseFieldMatrix<>(Complex.I.getField(), nodeCodeMap.size(), nodeCodeMap.size());

    for (LineInput line : lines) {
      String nodeA = line.getNodeA().getId();
      String nodeB = line.getNodeB().getId();
      int i = nodeCodeMap.get(nodeA);
      int j = nodeCodeMap.get(nodeB);
      int n = line.getParallelDevices(); // number of parallel lines, for LV set to 1

      Complex yIJ = new Complex(GridUtils.computeGij(line, sNom), GridUtils.computeBij(line, sNom));

      // if(yIJ.isNaN()) // TODO: replace
      // System.out.println("Fehler: NaN in Admittanz-Matrix zwischen " + nodeA + " und " + nodeB +
      // ". Einträge prüfen. Möglicherweise 0 in r- und x-Spalte der Leitung?");

      // Bei Admittanzen wird der Wert mit der Anzahl der parallelen Leitungen multipliziert.
      admittanceMatrix.addToEntry(i, i, yIJ.multiply(n));
      admittanceMatrix.addToEntry(
          i, i, new Complex(GridUtils.computeG0(line, sNom), GridUtils.computeB0(line, sNom)));
      // TODO: die ältere Implementierung hat hier noch mit einem Faktor multipliziert, der vom
      // Status des
      // zugehörigen Schalters abhing. Klären, ob das noch so aktuell ist oder eventuell auf neue
      // Implementierung
      // von Schaltern warten.

      admittanceMatrix.addToEntry(j, j, yIJ.multiply(n));
      admittanceMatrix.addToEntry(
          j, j, new Complex(GridUtils.computeG0(line, sNom), GridUtils.computeB0(line, sNom)));
      admittanceMatrix.addToEntry(i, j, yIJ.multiply(-n));
      admittanceMatrix.addToEntry(j, i, yIJ.multiply(-n));
    }

    return admittanceMatrix;
  }

  /**
   * Converts a list of Nodes to a list of OsmogridNodes
   *
   * @param nodes List of Nodes which shall be converted to OsmogridNodes
   * @return List of converted OsmogridNodes
   */
  public static List<OsmogridNode> getOsmogridNodeList(@NotNull List<Node> nodes) {
    List<OsmogridNode> osmogridNodes = new LinkedList<>();
    for (Node node : nodes) {
      OsmogridNode osmogridNode = new OsmogridNode(node);
      osmogridNodes.add(osmogridNode);
    }
    return osmogridNodes;
  }

  /**
   * Calculates the geo position as a Point from a given Latlon (net.morbz.osmonaut.osm). Uses the
   * WGS84 reference system.
   *
   * @param latLon Latlon from which the geo position shall be calculated
   * @return calculated Point from the given Latlon
   */
  public static Point latlonToPoint(@NotNull LatLon latLon) {
    GeometryFactory geoFactory = new GeometryFactory();
    Point geoPosition = geoFactory.createPoint(new Coordinate(latLon.getLon(), latLon.getLat()));
    geoPosition.setSRID(4326); // Use WGS84 reference system.
    return geoPosition;
  }

  public static org.locationtech.jts.geom.Point latLonToPointNew(LatLon latLon) {
    org.locationtech.jts.geom.GeometryFactory geometryFactory =
        new org.locationtech.jts.geom.GeometryFactory();
    org.locationtech.jts.geom.Point geoPosition =
        geometryFactory.createPoint(
            new org.locationtech.jts.geom.Coordinate(latLon.getLon(), latLon.getLat()));
    geoPosition.setSRID(4326); // Use WGS84 reference system.
    return geoPosition;
  }

  /**
   * Calculates the geo position as a {@link LineString} from a given collection of {@link
   * OsmogridNode}s.
   *
   * @param nodes Node list from which the geo position shall be calculated.
   * @return Calculated LineString from the given node list.
   */
  public static LineString nodesToLineString(Collection<OsmogridNode> nodes) {

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

  public static LineString lineStringFromNodeInputs(NodeInput nodeA, NodeInput nodeB) {
    org.locationtech.jts.geom.GeometryFactory geometryFactory =
        new org.locationtech.jts.geom.GeometryFactory();
    LineString geoPosition;

    org.locationtech.jts.geom.Coordinate[] coordinates =
        new org.locationtech.jts.geom.Coordinate[2];
    coordinates[0] =
        new org.locationtech.jts.geom.Coordinate(
            nodeA.getGeoPosition().getX(), nodeA.getGeoPosition().getY());
    coordinates[1] =
        new org.locationtech.jts.geom.Coordinate(
            nodeB.getGeoPosition().getX(), nodeB.getGeoPosition().getY());

    geoPosition = geometryFactory.createLineString(coordinates);
    geoPosition.setSRID(4326); // Use WGS84 reference system

    return geoPosition;
  }

  /**
   * Computes the branch conductance of this line
   *
   * @return Branch conductance G_ij between node A and B in p.u.
   */
  private static double computeGij(@NotNull LineInput line, @NotNull Quantity<Power> sNom) {
    // TODO was tun, wenn Netz inkonsistent (Knoten fehlen?) bisher:
    // if(nodeMap.containsValue(false)) return 0;
    // TODO: unify handling of sNom and vNom (currently used as line.getType().getvRated())

    double r_temp =
        line.getType()
            .getR()
            .to(PowerSystemUnits.OHM_PER_KILOMETRE)
            .multiply(line.getLength().to(PowerSystemUnits.KILOMETRE))
            .asType(ElectricResistance.class)
            .getValue()
            .doubleValue();
    double x_temp =
        line.getType()
            .getX()
            .to(PowerSystemUnits.OHM_PER_KILOMETRE)
            .multiply(line.getLength().to(PowerSystemUnits.KILOMETRE))
            .asType(ElectricResistance.class)
            .getValue()
            .doubleValue();

    double Z_n =
        Math.pow(line.getType().getvRated().to(Units.VOLT).getValue().doubleValue(), 2)
            / sNom.to(PowerSystemUnits.VOLTAMPERE).getValue().doubleValue(); // in Ohm

    Quantity<Dimensionless> r = Quantities.getQuantity(r_temp / Z_n, PowerSystemUnits.PU);
    Quantity<Dimensionless> x = Quantities.getQuantity(x_temp / Z_n, PowerSystemUnits.PU);

    double rVal = r.getValue().doubleValue(), xVal = x.getValue().doubleValue();
    if (rVal == 0) {
      return 0;
    } else if (xVal == 0) {
      return 1 / rVal;
    } else {
      return rVal / (rVal * rVal + xVal * xVal);
    }
  }

  /**
   * Computes the phase-to-ground conductance of this line
   *
   * @return Half of the phase-to-ground conductance G_0 in p.u. for direct usage in pi-equivalent
   *     circuit.
   */
  private static double computeG0(@NotNull LineInput line, @NotNull Quantity<Power> sNom) {
    // TODO was tun, wenn Netz inkonsistent (Knoten fehlen?) bisher:
    // if(nodeMap.containsValue(false)) return 0;
    // TODO: unify handling of sNom and vNom (currently used as line.getType().getvRated())

    double g_temp =
        line.getType()
            .getG()
            .to(PowerSystemUnits.SIEMENS_PER_KILOMETRE)
            .multiply(line.getLength().to(PowerSystemUnits.KILOMETRE))
            .asType(ElectricConductance.class)
            .getValue()
            .doubleValue();

    double Z_n =
        Math.pow(line.getType().getvRated().to(Units.VOLT).getValue().doubleValue(), 2)
            / sNom.to(PowerSystemUnits.VOLTAMPERE).getValue().doubleValue(); // in Ohm

    Quantity<Dimensionless> g = Quantities.getQuantity(g_temp * Z_n, PowerSystemUnits.PU);

    return g.getValue().doubleValue() / 2;
  }

  /**
   * Computes the branch susceptance of this line
   *
   * @return Branch susceptance B_ij between node A and B in p.u.
   */
  private static double computeBij(@NotNull LineInput line, @NotNull Quantity<Power> sNom) {
    // TODO was tun, wenn Netz inkonsistent (Knoten fehlen?) bisher:
    // if(nodeMap.containsValue(false)) return 0;
    // TODO: unify handling of sNom and vNom (currently used as line.getType().getvRated())

    double r_temp =
        line.getType()
            .getR()
            .to(PowerSystemUnits.OHM_PER_KILOMETRE)
            .multiply(line.getLength().to(PowerSystemUnits.KILOMETRE))
            .asType(ElectricResistance.class)
            .getValue()
            .doubleValue();
    double x_temp =
        line.getType()
            .getX()
            .to(PowerSystemUnits.OHM_PER_KILOMETRE)
            .multiply(line.getLength().to(PowerSystemUnits.KILOMETRE))
            .asType(ElectricResistance.class)
            .getValue()
            .doubleValue();

    double Z_n =
        Math.pow(line.getType().getvRated().to(Units.VOLT).getValue().doubleValue(), 2)
            / sNom.to(PowerSystemUnits.VOLTAMPERE).getValue().doubleValue(); // in Ohm

    Quantity<Dimensionless> r = Quantities.getQuantity(r_temp / Z_n, PowerSystemUnits.PU);
    Quantity<Dimensionless> x = Quantities.getQuantity(x_temp / Z_n, PowerSystemUnits.PU);

    double rVal = r.getValue().doubleValue(), xVal = x.getValue().doubleValue();
    if (xVal == 0) {
      return 0;
    } else if (rVal == 0) {
      return -1 / xVal;
    } else {
      return -xVal / (rVal * rVal + xVal * xVal);
    }
  }

  /**
   * Computes the phase-to-ground susceptance of this line
   *
   * @return Half of the phase-to-ground susceptance B_0 in p.u. for direct usage in pi-equivalent
   *     circuit.
   */
  private static double computeB0(@NotNull LineInput line, @NotNull Quantity<Power> sNom) {
    // TODO was tun, wenn Netz inkonsistent (Knoten fehlen?) bisher:
    // if(nodeMap.containsValue(false)) return 0;
    // TODO: unify handling of sNom and vNom (currently used as line.getType().getvRated())

    double b_temp =
        line.getType()
            .getB()
            .to(PowerSystemUnits.SIEMENS_PER_KILOMETRE)
            .multiply(line.getLength().to(PowerSystemUnits.KILOMETRE))
            .asType(ElectricConductance.class)
            .getValue()
            .doubleValue();

    double Z_n =
        Math.pow(line.getType().getvRated().to(Units.VOLT).getValue().doubleValue(), 2)
            / sNom.to(PowerSystemUnits.VOLTAMPERE).getValue().doubleValue(); // in Ohm

    Quantity<Dimensionless> b = Quantities.getQuantity(b_temp * Z_n, PowerSystemUnits.PU);

    return b.getValue().doubleValue() / 2;
  }
}
