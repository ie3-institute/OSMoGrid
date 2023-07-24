/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package utils

import edu.ie3.datamodel.models.input.NodeInput
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.datamodel.models.voltagelevels.VoltageLevel
import edu.ie3.osmogrid.cfg.OsmoGridConfig

import scala.jdk.CollectionConverters._

object SubGridContainerUtils {

  /** Filter for [[SubGridContainer]]. This method finds all mv nodes inside the
    * given containers. The found nodes are returned as two lists. The first
    * list contains all mv nodes that are connected to hv grids via a
    * transformer. The second list contains all mv nodes that are connected to
    * the lv grids via a transformer.
    *
    * @param lvGrids
    *   list of lv [[SubGridContainer]]
    * @param hvGrids
    *   list of hv [[SubGridContainer]]
    * @param cfg
    *   containing information about mv voltage levels
    * @return
    *   a tuple of two lists
    */
  def filter(
      lvGrids: List[SubGridContainer],
      hvGrids: List[SubGridContainer],
      cfg: OsmoGridConfig.Generation.Mv
  ): (List[NodeInput], List[NodeInput]) = {
    /* returns a list of all mv voltage levels */
    val mvVoltLvl = VoltageLevelUtils.parseMv(cfg.voltageLevel)
    /* gets all hv-mv and mv-lv nodes */
    (getNodes(mvVoltLvl, hvGrids), getNodes(mvVoltLvl, lvGrids))
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
