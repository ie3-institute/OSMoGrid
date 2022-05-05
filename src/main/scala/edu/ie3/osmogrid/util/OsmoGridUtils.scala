/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package de.osmogrid.util

import edu.ie3.datamodel.models.input.NodeInput
import edu.ie3.util.OneToOneMap
import edu.ie3.util.quantities.PowerSystemUnits

import java.util
import java.util.stream.Collectors
import javax.measure.Quantity
import javax.measure.quantity.Area
import javax.measure.quantity.Power
import net.morbz.osmonaut.osm.LatLon
import net.morbz.osmonaut.osm.Node
import org.locationtech.jts.geom.{GeometryFactory, LineString, Point}
import tech.units.indriya.quantity.Quantities
import tech.units.indriya.unit.Units

import scala.jdk.CollectionConverters.*

object OsmoGridUtils {

  /** area correction factor set to 0 (see calGeo2qm for details) */
  val GEO2QM_CORRECTION: Quantity[Area] =
    Quantities.getQuantity(0.0, Units.SQUARE_METRE)

  /** Converts a list of Nodes to a list of OsmogridNodes
    *
    * @param nodes
    *   List of Nodes which shall be converted to OsmogridNodes
    * @return
    *   List of converted OsmogridNodes
    */
  def getOsmoGridNodeList(nodes: List[Node]): List[OsmGridNode] = {
    val osmGridNodes = new util.LinkedList[OsmGridNode]
    for (node <- nodes) {
      val osmGridNode = new OsmGridNode(node)
      osmGridNodes.add(osmGridNode)
    }
    osmGridNodes
  }

  /** Map geom points to java awt points. Can be removed when GeoUtils in PSU is
    * fixed and tested.
    *
    * @param points
    * @return
    */
  def toJavaAwtPoints(points: Set[Point]): Set[Point] =
    points.stream.map(OsmoGridUtils.toJavaAwtPoint).collect(Collectors.toSet)

  /** Map geom points to java awt points. Can be removed when GeoUtils in PSU is
    * fixed and tested.
    *
    * @param point
    * @return
    */
  def toJavaAwtPoint(point: Point) =
    new Point(point.getX.toInt, point.getY.toInt)

  /** Calculates the power value of a household load based on the provided
    * building area and the provided average power density value and the
    * provided average household area size
    *
    * @param area
    * @param density
    */
  def calcPower(
      area: Quantity[Area],
      density: Quantity[PowerDensity]
  ): Quantity[Power] = {
    val power = area
      .to(Units.SQUARE_METRE)
      .multiply(density.to(OsmoGridUnits.WATT_PER_SQUARE_METRE))
      .asType(classOf[Power])
      .to(PowerSystemUnits.KILOWATT)
    QuantityUtils.ceil(power).asType(classOf[Power])
  }

  /** Calculates the geo position as a {@link LineString} from a given
    * collection of {@link OsmGridNode}s.
    *
    * @param nodes
    *   Node list from which the geo position shall be calculated.
    * @return
    *   Calculated LineString from the given node list.
    */
  def nodesToLineString(nodes: Collection[OsmGridNode]): LineString = {
    val latLons = nodes.stream.map(Node.getLatlon).collect(Collectors.toSet)
    latLonsToLineString(latLons)
  }

  /** Calculates the geo position as a {@link LineString} from a given
    * collection of {@link LatLon}s.
    *
    * @param latLons
    *   LatLon list from which the geo position shall be calculated.
    * @return
    *   Calculated LineString from the given LatLon list.
    */
  def latLonsToLineString(latLons: Collection[LatLon]): LineString = {
    val geometryFactory = new GeometryFactory
    var geoPosition = null
    if (latLons.size >= 2) {
      val coordinates = new Array[Coordinate](latLons.size)
      var cnt = 0
      for (latLon <- latLons) {
        coordinates({ cnt += 1; cnt - 1 }) =
          new Coordinate(latLon.getLon, latLon.getLat)
      }
      geoPosition = geometryFactory.createLineString(coordinates)
      geoPosition.setSRID(4326) // Use WGS84 reference system

    } else { // If there are less than two geo coordinates, set geo position to null
      geoPosition = null
    }
    geoPosition
  }
  def buildNodeCodeMap(
      nodes: Collection[NodeInput]
  ): OneToOneMap[String, Integer] = {
    val nodeCodeMap = new OneToOneMap[String, Integer](nodes.size)
    var counter = 0
    for (node <- nodes) {
      nodeCodeMap.put(node.getId, counter)
      counter += 1
    }
    nodeCodeMap
  }
}
