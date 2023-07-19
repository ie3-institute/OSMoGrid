/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.mv

import akka.actor.typed.scaladsl.ActorContext
import edu.ie3.datamodel.models.input.NodeInput
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.datamodel.models.voltagelevels.VoltageLevel
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.util.geo.GeoUtils
import org.locationtech.jts.geom.{Coordinate, Polygon}
import org.locationtech.jts.triangulate.VoronoiDiagramBuilder
import utils.VoltageLevelUtils

import scala.jdk.CollectionConverters._

// TODO: Parts of this or maybe all of this could be moved to GeoUtils (PowerSystemUtils).
object VoronoiHelper {

  /** A voronoi polynomial.
    * @param areaNumber
    *   subgrid number
    * @param hvNode
    *   hv-mv transition point
    * @param mvNodes
    *   list of mv-lv transition points
    */
  final case class VoronoiPolynomial(
      areaNumber: Int,
      hvNode: NodeInput,
      mvNodes: List[NodeInput]
  )

  /** Method to calculate voronoi polynomials.
    *
    * @param lvGrids
    *   list of lv [[SubGridContainer]]
    * @param hvGrids
    *   list of hv [[SubGridContainer]]
    * @param cfg
    *   config
    * @param ctx
    *   actor ref
    * @return
    *   a list of [[VoronoiPolynomial]]
    */
  def calculateVoronoiPolynomials(
      lvGrids: List[SubGridContainer],
      hvGrids: List[SubGridContainer],
      cfg: OsmoGridConfig.Generation.Mv,
      ctx: ActorContext[MvRequest]
  ): List[VoronoiPolynomial] =
    calculateVoronoiPolynomials(lvGrids, hvGrids, cfg, ctx, 99)

  /** Method to calculate voronoi polynomials.
    * @param lvGrids
    *   list of lv [[SubGridContainer]]
    * @param hvGrids
    *   list of hv [[SubGridContainer]]
    * @param cfg
    *   config
    * @param ctx
    *   actor ref
    * @param startNumber
    *   subgrid number to start with
    * @return
    *   a list of [[VoronoiPolynomial]]
    */
  def calculateVoronoiPolynomials(
      lvGrids: List[SubGridContainer],
      hvGrids: List[SubGridContainer],
      cfg: OsmoGridConfig.Generation.Mv,
      ctx: ActorContext[MvRequest],
      startNumber: Int
  ): List[VoronoiPolynomial] = {
    var areaNumber = startNumber

    /* filters all nodes in all subgrid containers */
    val (hvToMvNodes, mvToLvNodes) = filter(lvGrids, hvGrids, cfg)

    /* list of nodes that are not assigned yet */
    val notAssignedNodes: List[NodeInput] = mvToLvNodes

    /* creates a voronoi diagram with the hv-mv nodes as sites */
    getPolygons(hvToMvNodes).map { case (hvToMvNode, polygon) =>
      /* assigns the mv-lv nodes to the found polygons */
      val mvNodes: List[NodeInput] = notAssignedNodes.flatMap { lvNode =>
        if (polygon.contains(lvNode.getGeoPosition)) {
          Some(lvNode)
        } else {
          None
        }
      }

      /* removes all found elements from list to reduce calculation each time */
      notAssignedNodes.asJava.removeAll(mvNodes.asJava)

      /* each voronoi polynomial should have an unique area number */
      areaNumber += 1
      ctx.log.debug("Create new voronoi polynomial.")

      /* create a new VoronoiPolynomial */
      VoronoiPolynomial(areaNumber, hvToMvNode, mvNodes)
    }.toList
  }

  /** This method uses a [[VoronoiDiagramBuilder]] to generate a voronoi diagram
    * with the given nodes a its sites. After the diagram is generated the
    * resulting polygons are mapped to the corresponding hv-mv node.
    * @param nodes
    *   hv-mv transition points
    * @return
    *   a map: hv-mv transition points to polygons
    */
  private def getPolygons(
      nodes: List[NodeInput]
  ): Map[NodeInput, Polygon] = {
    /* builds a map: coordinates to nodes */
    val transitionPoints: Map[Coordinate, NodeInput] = nodes.map { node =>
      (node.getGeoPosition.getCoordinate, node)
    }.toMap

    /* creates a new VoronoiDiagramBuilder */
    val builder: VoronoiDiagramBuilder = new VoronoiDiagramBuilder()
    builder.setSites(transitionPoints.keySet.asJava)

    /* finds all subdivisions and returns them as polygons */
    builder.getSubdivision
      .getVoronoiCellPolygons(
        GeoUtils.DEFAULT_GEOMETRY_FACTORY
      )
      .asInstanceOf[List[Polygon]]
      .map { polygon =>
        /* maps all polygons to the correct hv-mv node */
        (transitionPoints(polygon.getCoordinate), polygon)
      }
      .toMap
  }

  /** Filter for [[SubGridContainer]]. This method finds all mv nodes inside the
    * given containers. The found nodes are returned as two lists. The first
    * list contains all mv nodes that are connected to hv grids via a
    * transformer. The second list contains all mv nodes that are connected to
    * the lv grids via a transformer.
    * @param lvGrids
    *   list of lv [[SubGridContainer]]
    * @param hvGrids
    *   list of hv [[SubGridContainer]]
    * @param cfg
    *   containing information about mv voltage levels
    * @return
    *   a tuple of two lists
    */
  private def filter(
      lvGrids: List[SubGridContainer],
      hvGrids: List[SubGridContainer],
      cfg: OsmoGridConfig.Generation.Mv
  ): (List[NodeInput], List[NodeInput]) = {
    /* returns a list of all mv voltage levels */
    val mvVoltLvl = VoltageLevelUtils.getVoltLvl(cfg.voltageLevel.mv)
    /* gets all hv-mv and mv-lv nodes */
    (getNodes(mvVoltLvl, hvGrids), getNodes(mvVoltLvl, lvGrids))
  }

  /** Method to return all [[NodeInput]]'s of all given [[SubGridContainer]]
    * that have one of the given [[VoltageLevel]]'s.
    * @param voltageLevels
    *   list of all voltage levels
    * @param subGrids
    *   list of [[SubGridContainer]]
    * @return
    *   a list of [[NodeInput]]'s
    */
  private def getNodes(
      voltageLevels: List[VoltageLevel],
      subGrids: List[SubGridContainer]
  ): List[NodeInput] = {
    subGrids.flatMap(subgrid => {
      /* finds all transformer in the given subgrid */
      subgrid.getRawGrid.getTransformer2Ws.asScala
        .flatMap(transformer =>
          /* gets all nodes connected to a given transformer and returns all nodes that have a mv voltage level */
          transformer
            .allNodes()
            .asScala
            .filter(node => voltageLevels.contains(node.getVoltLvl))
            .toSeq
        )
        .toList
    })
  }
}
