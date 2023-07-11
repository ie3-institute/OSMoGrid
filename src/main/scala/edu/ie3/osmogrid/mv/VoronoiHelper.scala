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
import utils.VoltageLevelUtils

import scala.jdk.CollectionConverters._

final case class VoronoiHelper(
    mvToLvNodes: List[NodeInput],
    hvToMvNodes: List[NodeInput],
    cfg: OsmoGridConfig.Generation.Mv,
    ctx: ActorContext[MvRequest]
) {

  // splits the given area into mv grid areas
  // these areas can be used by a VoronoiCoordinator
  def calculatePolynomials(): List[VoronoiPolynomial] = {
    List.empty
  }
}

object VoronoiHelper {
  def apply(
      lvGrids: List[SubGridContainer],
      hvGrids: List[SubGridContainer],
      cfg: OsmoGridConfig.Generation.Mv,
      ctx: ActorContext[MvRequest]
  ): VoronoiHelper = {
    val mvVoltLvl = VoltageLevelUtils.getMvVoltLvl(cfg.voltageLevel)

    val mvToLvNodes = getNodes(mvVoltLvl, lvGrids)
    val hvToMvNodes = getNodes(mvVoltLvl, hvGrids)

    VoronoiHelper(mvToLvNodes, hvToMvNodes, cfg, ctx)
  }

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
