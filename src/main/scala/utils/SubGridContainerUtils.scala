/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package utils

import edu.ie3.datamodel.models.input.NodeInput
import edu.ie3.datamodel.models.input.container.{
  GraphicElements,
  RawGridElements,
  SubGridContainer,
  SystemParticipants
}
import edu.ie3.datamodel.models.voltagelevels.VoltageLevel
import edu.ie3.osmogrid.cfg.OsmoGridConfig

import scala.jdk.CollectionConverters._

object SubGridContainerUtils {

  // filter all mv-lv nodes in lv sub grid containers
  def filterLv(
      lvGrids: List[SubGridContainer],
      cfg: OsmoGridConfig.Generation.Mv
  ): List[NodeInput] = {
    /* returns a list of all mv voltage levels */
    val mvVoltLvl = VoltageLevelUtils.parseMv(cfg.voltageLevel)
    /* gets all mv-lv nodes */
    getNodes(mvVoltLvl, lvGrids)
  }

  // filter all hv-mv nodes in hv sub grid containers
  def filterHv(
      hvGrids: List[SubGridContainer],
      cfg: OsmoGridConfig.Generation.Mv
  ): List[NodeInput] = {
    /* returns a list of all mv voltage levels */
    val mvVoltLvl = VoltageLevelUtils.parseMv(cfg.voltageLevel)
    /* gets all hv-mv nodes */
    getNodes(mvVoltLvl, hvGrids)
  }

  /** Method to return all [[NodeInput]]'s of all given [[SubGridContainer]]
    * that have one of the given [[VoltageLevel]]'s.
    *
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
