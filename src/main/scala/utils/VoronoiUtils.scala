/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package utils

import akka.actor.typed.scaladsl.ActorContext
import edu.ie3.datamodel.models.input.NodeInput
import edu.ie3.util.geo.GeoUtils
import org.locationtech.jts.geom.{Coordinate, Polygon}
import org.locationtech.jts.triangulate.VoronoiDiagramBuilder

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
    */
  final case class VoronoiPolygon(
      transitionPointToHigherVoltLvl: NodeInput,
      transitionPointsToLowerVoltLvl: List[NodeInput],
      polygon: Option[Polygon]
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
      *   true, if the node is inside
      */
    def containsNode(node: NodeInput): Boolean = {
      polygon match {
        case Some(value) => value.contains(node.getGeoPosition)
        case None        => false
      }
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
    updatePolygons(polygons, additionalPoints, ctx)
  }

  /** Method to add a list of [[NodeInput]] to a list of [[VoronoiPolygon]].
    * @param polygons
    *   a list of [[VoronoiPolygon]]
    * @param nodes
    *   that should be added
    * @param ctx
    *   actor context
    * @return
    *   an updated list of [[VoronoiPolygon]]
    */
  def updatePolygons[T](
      polygons: List[VoronoiPolygon],
      nodes: List[NodeInput],
      ctx: ActorContext[T]
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
      ctx.log.debug(
        s"The following nodes could not be assigned: $notAssignedNodes."
      )
    } else {
      ctx.log.debug("All nodes have been assigned to the polygons.")
    }

    (updatedPolygons, notAssignedNodes)
  }

  /** This method uses a [[VoronoiDiagramBuilder]] to generate a voronoi diagram
    * with the given nodes a its sites. After the diagram is generated the
    * resulting polygons are mapped to the corresponding hv-mv node.
    *
    * @param nodes
    *   hv-mv transition points
    * @return
    *   a map: hv-mv transition points to polygons
    */
  def createPolygons(
      nodes: List[NodeInput]
  ): List[VoronoiPolygon] = {
    if (nodes.isEmpty) {
      List.empty
    } else {
      /* retrieves the coordinates of all nodes */
      val transitionPoints: List[Coordinate] = nodes.par.map { node =>
        node.getGeoPosition.getCoordinate
      }.toList

      /* creates a new VoronoiDiagramBuilder */
      val builder: VoronoiDiagramBuilder = new VoronoiDiagramBuilder()
      builder.setSites(transitionPoints.asJava)

      /* finds all subdivisions and returns them as polygons */
      val polygons: List[Polygon] = builder.getSubdivision
        .getVoronoiCellPolygons(
          GeoUtils.DEFAULT_GEOMETRY_FACTORY
        )
        /* necessary to get proper polygons */
        .asScala
        .toList
        .map(p => p.asInstanceOf[Polygon])

      nodes.par.map { node =>
        val polygon = polygons.par
          .filter(polygon => polygon.contains(node.getGeoPosition))
          .toSeq
        /* creates the voronoi polygon with all known information */
        VoronoiPolygon(node, List.empty, polygon.headOption)
      }.toList
    }
  }
}
