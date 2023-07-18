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

final case class VoronoiHelper(
    mvToLvNodes: List[NodeInput],
    hvToMvNodes: List[NodeInput],
    cfg: OsmoGridConfig.Generation.Mv,
    ctx: ActorContext[MvRequest],
    areaNumber: Int
) {

  // splits the given area into mv grid areas
  // these areas can be used by a VoronoiCoordinator
  def calculateVoronoiPolynomials(): List[VoronoiPolynomial] = {
    val polynomials: Map[NodeInput, Polygon] = getVoronoiPolynomials(
      hvToMvNodes
    )

    val notAssignedNodes: List[NodeInput] = mvToLvNodes

    polynomials
      .map { case (node, polygon) =>
        val lvNodes: List[NodeInput] = notAssignedNodes.flatMap { lvNode =>
          if (polygon.contains(lvNode.getGeoPosition)) {
            Some(lvNode)
          } else {
            None
          }
        }
        // removes all found elements from list to reduce calculation each time
        notAssignedNodes.asJava.removeAll(lvNodes.asJava)
        (node, lvNodes)
      }
      .map { case (hvToMvNode, mvNodes) =>
        // each voronoi polynomial should have an unique area number
        areaNumber += 1
        VoronoiPolynomial(areaNumber, hvToMvNode, mvNodes)
      }
      .toList
  }

  // sets up the voronoi builder
  // returns a map: hv node to polygon
  private def getVoronoiPolynomials(
      nodes: List[NodeInput]
  ): Map[NodeInput, Polygon] = {
    val transitionPoints: Map[Coordinate, NodeInput] = nodes.map { node =>
      (node.getGeoPosition.getCoordinate, node)
    }.toMap

    val builder: VoronoiDiagramBuilder = new VoronoiDiagramBuilder()
    builder.setSites(transitionPoints.keySet.asJava)

    builder.getSubdivision
      .getVoronoiCellPolygons(
        GeoUtils.DEFAULT_GEOMETRY_FACTORY
      )
      .asInstanceOf[List[Polygon]]
      .map { polygon =>
        (transitionPoints(polygon.getCoordinate), polygon)
      }
      .toMap
  }
}

object VoronoiHelper {
  // method for applying a list of hv SubGridContainers and a list of lv SubGridContainers to a VoronoiHelper
  def apply(
      lvGrids: List[SubGridContainer],
      hvGrids: List[SubGridContainer],
      cfg: OsmoGridConfig.Generation.Mv,
      ctx: ActorContext[MvRequest]
  ): VoronoiHelper = {
    val mvVoltLvl = VoltageLevelUtils.getMvVoltLvl(cfg.voltageLevel)

    val mvToLvNodes = getNodes(mvVoltLvl, lvGrids)
    val hvToMvNodes = getNodes(mvVoltLvl, hvGrids)

    VoronoiHelper(mvToLvNodes, hvToMvNodes, cfg, ctx, 100)
  }

  // method for applying a list of hv SubGridContainers and a list of lv SubGridContainers to a VoronoiHelper
  def apply(
      lvGrids: List[SubGridContainer],
      hvGrids: List[SubGridContainer],
      cfg: OsmoGridConfig.Generation.Mv,
      ctx: ActorContext[MvRequest],
      areaNumber: Int
  ): VoronoiHelper = {
    val mvVoltLvl = VoltageLevelUtils.getMvVoltLvl(cfg.voltageLevel)

    val mvToLvNodes = getNodes(mvVoltLvl, lvGrids)
    val hvToMvNodes = getNodes(mvVoltLvl, hvGrids)

    VoronoiHelper(mvToLvNodes, hvToMvNodes, cfg, ctx, areaNumber)
  }

  // returns all nodes of a given voltage level from a list of SubGridContainers
  private def getNodes(
      voltageLevel: List[VoltageLevel],
      subGrids: List[SubGridContainer]
  ): List[NodeInput] = {
    subGrids.flatMap(subgrid => {
      subgrid.getRawGrid.getTransformer2Ws.asScala
        .flatMap(transformer =>
          transformer
            .allNodes()
            .asScala
            .filter(node => voltageLevel.contains(node.getVoltLvl))
            .toSeq
        )
        .toList
    })
  }
}
