/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package utils

import akka.actor.typed.scaladsl.ActorContext
import edu.ie3.datamodel.models.input.NodeInput
import edu.ie3.util.exceptions.GeoException
import edu.ie3.util.geo.GeoUtils
import edu.ie3.util.geo.GeoUtils.buildCoordinate
import org.locationtech.jts.geom.{Coordinate, Envelope, Polygon}
import org.locationtech.jts.triangulate.VoronoiDiagramBuilder
import org.slf4j.Logger
import tech.units.indriya.quantity.Quantities
import tech.units.indriya.unit.Units.METRE

import scala.collection.parallel.CollectionConverters.ImmutableIterableIsParallelizable
import scala.jdk.CollectionConverters._

// TODO: Parts of this or maybe all of this could be moved to GeoUtils (PowerSystemUtils).
object VoronoiUtils {

  /** A voronoi polygons.
    *
    * @param transitionPointToHigherVoltLvl
    *   hv-mv transition point
    * @param transitionPointsToLowerVoltLvl
    *   list of mv-lv transition points
    * @param polygon
    *   a [[Polygon]]
    */
  final case class VoronoiPolygon(
      transitionPointToHigherVoltLvl: NodeInput,
      transitionPointsToLowerVoltLvl: List[NodeInput],
      polygon: Polygon
  ) {

    /** Method to add nodes, that are connected to a lower voltage level, to
      * this voronoi polygon.
      * @param nodes
      *   that should be added
      * @return
      *   a copy of this object
      */
    def addTransitionPointsToLowerVoltLvl(
        nodes: List[NodeInput]
    ): VoronoiPolygon = {
      copy(transitionPointsToLowerVoltLvl =
        transitionPointsToLowerVoltLvl ++ nodes
      )
    }

    /** Method to check if a given [[NodeInput]] is located inside this
      * [[VoronoiPolygon]].
      * @param node
      *   to be checked
      * @return
      *   true, if the node is inside, or false if the node is not inside the
      *   polygon
      */
    def containsNode(node: NodeInput): Boolean = {
      polygon.contains(node.getGeoPosition)
    }
  }

  /** Method to create [[VoronoiPolygon]]. This method takes transition points,
    * that are used to generate a voronoi diagram, and additional points that
    * are added to the polygons.
    * @param transitionPoints
    * i.e. hv to mv transition points
    * @param additionalPoints
    * i.e. mv to lv transition points
    * @param ctx
    *   context
    * @tparam T
    *   type
    * @return
    *   a tuple containing the [[VoronoiPolygon]] and a list of not assigned
    *   nodes
    */
  def createVoronoiPolygons[T](
      transitionPoints: List[NodeInput],
      additionalPoints: List[NodeInput],
      ctx: ActorContext[T]
  ): (List[VoronoiPolygon], List[NodeInput]) = {
    val polygons = createPolygons(transitionPoints)

    /* updates the polygons with additional nodes */
    updatePolygons(polygons, additionalPoints, ctx.log)
  }

  /** Method to add a list of [[NodeInput]] to a list of [[VoronoiPolygon]].
    * @param polygons
    *   a list of [[VoronoiPolygon]]
    * @param nodes
    *   that should be added
    * @param log
    *   logger
    * @return
    *   an updated list of [[VoronoiPolygon]]
    */
  private def updatePolygons(
      polygons: List[VoronoiPolygon],
      nodes: List[NodeInput],
      log: Logger
  ): (List[VoronoiPolygon], List[NodeInput]) = {
    /* list of nodes that are not assigned yet */
    var notAssignedNodes: List[NodeInput] = nodes

    val updatedPolygons = polygons.map { polygon =>
      /* assigns the mv-lv nodes to the found polygons */
      val mvNodes: List[NodeInput] = notAssignedNodes.par
        .filter(node => polygon.containsNode(node))
        .toList

      /* removes all found elements from list to reduce calculation each time */
      notAssignedNodes = notAssignedNodes.diff(mvNodes)

      /* replace the polygon with a copy that contains */
      polygon.addTransitionPointsToLowerVoltLvl(mvNodes)
    }

    /* logs a debug message */
    if (notAssignedNodes.isEmpty) {
      log.debug(
        s"The following nodes could not be assigned: $notAssignedNodes."
      )
    } else {
      log.debug("All nodes have been assigned to the polygons.")
    }

    (updatedPolygons, notAssignedNodes)
  }

  /** This method uses a [[VoronoiDiagramBuilder]] to generate a voronoi diagram
    * with the given nodes a its sites. After the diagram is generated the
    * resulting polygons are mapped to the corresponding hv-mv node. The found
    * [[Polygon]] is also added to the [[VoronoiPolygon]].
    *
    * @param nodes
    *   hv-mv transition points
    * @return
    *   a map: hv-mv transition points to polygons
    */
  private def createPolygons(
      nodes: List[NodeInput]
  ): List[VoronoiPolygon] = {
    if (nodes.isEmpty) {
      List.empty
    } else if (nodes.size == 1) {
      val node = nodes(0)
      val coordinate = node.getGeoPosition

      // in order to properly use the voronoi diagram, some helping coordinates need to be added
      // without these additional coordinates no polygon is returned by the voronoi builder
      val envelope: Envelope = GeoUtils.calculateBoundingBox(
        coordinate,
        Quantities.getQuantity(110000, METRE)
      )

      val coordinates = List(
        coordinate.getCoordinate,
        buildCoordinate(envelope.getMaxY, envelope.getMaxX),
        buildCoordinate(envelope.getMaxY, envelope.getMinX),
        buildCoordinate(envelope.getMinY, envelope.getMinX),
        buildCoordinate(envelope.getMinY, envelope.getMaxX)
      )

      // with this the previously added coordinates will be filtered out, because we only want the actual polygon for the given NodeInput
      val polygons = useBuilder(coordinates).filter(polygon =>
        polygon.contains(node.getGeoPosition)
      )

      // if more than one point is present after filtering, an exception is thrown
      if (polygons.size > 1) {
        throw new GeoException(
          "Number of returned polygons should equal 1, but " + polygons.size + " were returned."
        )
      } else {
        List(VoronoiPolygon(node, List.empty, polygons(0)))
      }
    } else {
      /* retrieves the coordinates of all nodes */
      val transitionPoints: List[Coordinate] = nodes.par.map { node =>
        node.getGeoPosition.getCoordinate
      }.toList

      // calling the builder to generate the polygons
      val polygons: List[Polygon] = useBuilder(transitionPoints)

      nodes.par.map { node =>
        val polygon = polygons.par
          .filter(polygon => polygon.contains(node.getGeoPosition))
          .toSeq
        /* creates the voronoi polygon with all known information */
        VoronoiPolygon(node, List.empty, polygon(0))
      }.toList
    }
  }

  /** This method uses the [[VoronoiDiagramBuilder]] and the provided
    * [[Coordinate]] to generate a list of [[Polygon]].
    *
    * @param nodes
    *   centres of the generated polygons
    * @return
    *   a list of [[Polygon]]
    */
  private def useBuilder(nodes: List[Coordinate]): List[Polygon] = {
    /* creates a new VoronoiDiagramBuilder */
    val builder: VoronoiDiagramBuilder = new VoronoiDiagramBuilder()
    builder.setSites(nodes.asJava)

    /* finds all subdivisions and returns them as polygons */
    builder.getSubdivision
      .getVoronoiCellPolygons(
        GeoUtils.DEFAULT_GEOMETRY_FACTORY
      )
      /* necessary to get proper polygons */
      .asScala
      .toList
      .map(p => p.asInstanceOf[Polygon])
  }
}
