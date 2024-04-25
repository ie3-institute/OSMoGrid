/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package utils

import edu.ie3.datamodel.models.input.NodeInput
import edu.ie3.datamodel.models.voltagelevels.GermanVoltageLevelUtils
import edu.ie3.osmogrid.exception.OsmDataException
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.util.geo.GeoUtils
import edu.ie3.util.geo.RichGeometries.RichPolygon
import edu.ie3.util.osm.OsmUtils.GeometryUtils.buildPolygon
import edu.ie3.util.osm.model.OsmEntity.Way.ClosedWay
import edu.ie3.util.osm.model.OsmEntity.{Node, Way}
import edu.ie3.util.quantities.PowerSystemUnits
import edu.ie3.util.quantities.QuantityUtils.{RichQuantity, RichQuantityDouble}
import edu.ie3.util.quantities.interfaces.Irradiance
import org.locationtech.jts.algorithm.Centroid
import org.locationtech.jts.geom.{Coordinate, Polygon}
import tech.units.indriya.ComparableQuantity
import tech.units.indriya.unit.Units

import java.util
import java.util.UUID
import javax.measure.quantity.{Area, Power}
import scala.collection.parallel.ParSeq
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success}

object OsmoGridUtils {

  /** Checks whether or not the center of a building is within a landuse
    *
    * @param buildingCenter
    *   the building center
    * @param landuses
    *   the landuse
    * @return
    *   whether or not the center of a building is within a landuse
    */
  def isInsideLanduse(
      buildingCenter: Coordinate,
      landuses: ParSeq[Polygon]
  ): Boolean =
    landuses.exists(_.containsCoordinate(buildingCenter))

  def safeBuildPolygon(
      closedWay: ClosedWay,
      nodes: Map[Long, Node]
  ): Polygon = {
    buildPolygon(closedWay, nodes) match {
      case Success(polygon) => polygon
      case Failure(exception) =>
        throw OsmDataException(
          s"Could not build polygon from closed way: $closedWay. Exception: ",
          exception
        )
    }
  }

  /** Calculates the power value of a household load based on the provided
    * building area and the provided average power density value and the
    * provided average household area size
    *
    * @param area
    *   area of the household
    * @param powerDensity
    *   average power per area
    */
  def calcHouseholdPower(
      area: ComparableQuantity[Area],
      powerDensity: ComparableQuantity[Irradiance]
  ): ComparableQuantity[Power] = {
    val power = area
      .to(Units.SQUARE_METRE)
      .multiply(powerDensity.to(PowerSystemUnits.WATT_PER_SQUAREMETRE))
      .asType(classOf[Power])
      .to(PowerSystemUnits.KILOWATT)
    power.round(4)
  }

  /** Builds a weighted street graph out ways and nodes.
    *
    * @param ways
    *   the ways
    * @param nodes
    *   the nodes
    * @return
    *   the street graph
    */
  def buildStreetGraph(
      ways: Seq[Way],
      nodes: Map[Long, Node]
  ): OsmGraph = {
    val graph = new OsmGraph()
    ways.foreach(way => {
      val nodeIds = way.nodes
      nodeIds.sliding(2).foreach { case Seq(nodeAId, nodeBId) =>
        (nodes.get(nodeAId), nodes.get(nodeBId)) match {
          case (Some(nodeA), Some(nodeB)) =>
            graph.addVertex(nodeA)
            graph.addVertex(nodeB)
            graph.addWeightedEdge(nodeA, nodeB)

          case (None, _) =>
            throw new IllegalArgumentException(
              s"Node $nodeAId of Way ${way.id} is not within our nodes mapping"
            )
          case (_, None) =>
            throw new IllegalArgumentException(
              s"Node $nodeBId of Way ${way.id} is not within our nodes mapping"
            )
        }
      }
    })
    graph
  }

  /** Method to find all unique connections. If less than two nodes are
    * provided, an empty list is returned. <p> Uniqueness: a -> b == b -> a
    *
    * @param nodes
    *   list of nodes
    * @return
    *   a list of all unique connections
    */
  def getAllUniqueCombinations[T](
      nodes: List[T]
  ): List[(T, T)] = {
    if (nodes.size < 2) {
      List.empty
    } else if (nodes.size == 2) {
      List((nodes(0), nodes(1)))
    } else {
      val connections: util.List[(T, T)] =
        new util.ArrayList[(T, T)]

      // algorithm to find all unique combinations
      nodes.foreach(nodeA => {
        nodes.foreach(nodeB => {
          // it makes no sense to connect a node to itself => nodeA and nodeB cannot be the same
          if (nodeA != nodeB) {
            // two combinations possible
            val t1 = (nodeA, nodeB)
            val t2 = (nodeB, nodeA)

            // if none of the combinations is already added, the first combination is added
            if (!connections.contains(t1) && !connections.contains(t2)) {
              connections.add(t1)
            }
          }
        })
      })

      // returns all unique connections
      connections.asScala.toList
    }
  }

  /** This method is used for medium voltage grid generation were we do not have
    * actual hv grids. Therefore this method spawns a new hv node.
    * @param mvNodes
    *   list of all mv nodes
    * @return
    *   a new hv node
    */
  def spawnDummyHvNode(mvNodes: Seq[NodeInput]): NodeInput = {
    if (mvNodes.isEmpty) {
      throw new IllegalArgumentException(
        "No mv nodes were provided! Therefore no hv node can be spawned."
      )
    }

    val node = if (mvNodes.length < 3) {
      mvNodes(0)
    } else {
      val hull = GeoUtils.buildConvexHull(
        mvNodes.map { n => n.getGeoPosition.getCoordinate }.toSet.asJava
      )

      Option(new Centroid(hull).getCentroid) match {
        case Some(coordinate) =>
          val sortedList = mvNodes
            .map { node: NodeInput =>
              (
                node,
                GeoUtils.calcHaversine(
                  coordinate,
                  node.getGeoPosition.getCoordinate
                )
              )
            }
            .sortBy(_._2)

          // returns the node that is closest to the center
          sortedList(0)._1
        case None =>
          mvNodes(0)
      }
    }

    new NodeInput(
      UUID.randomUUID(),
      s"Hv node of ${node.getId}",
      1d.asPu,
      true,
      node.getGeoPosition,
      GermanVoltageLevelUtils.HV,
      node.getSubnet
    )
  }
}
