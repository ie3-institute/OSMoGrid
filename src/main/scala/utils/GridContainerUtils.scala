/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package utils

import edu.ie3.datamodel.models.input.NodeInput
import edu.ie3.datamodel.models.input.connector.TransformerInput
import edu.ie3.datamodel.models.input.container.{
  GraphicElements,
  JointGridContainer,
  RawGridElements,
  SubGridContainer,
  SystemParticipants
}
import edu.ie3.datamodel.models.voltagelevels.VoltageLevel
import edu.ie3.osmogrid.cfg.OsmoGridConfig

import scala.jdk.CollectionConverters._

object GridContainerUtils {

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

  /** Method for combining two [[JointGridContainer]] into a single one.
    *
    * @param containerA
    *   first container
    * @param containerB
    *   second container
    * @return
    *   a new [[JointGridContainer]]
    */
  def combine(
      containerA: JointGridContainer,
      containerB: JointGridContainer
  ): JointGridContainer = {
    val rawGridElements = containerA.getRawGrid.allEntitiesAsList()
    val participants = containerA.getSystemParticipants.allEntitiesAsList()
    val graphicElements = containerA.getGraphics.allEntitiesAsList()

    rawGridElements.addAll(containerB.getRawGrid.allEntitiesAsList())
    participants.addAll(containerB.getSystemParticipants.allEntitiesAsList())
    graphicElements.addAll(containerB.getGraphics.allEntitiesAsList())

    new JointGridContainer(
      containerA.getGridName,
      new RawGridElements(rawGridElements),
      new SystemParticipants(participants),
      new GraphicElements(graphicElements)
    )
  }

  // removes nodes and transformers associated with a given uuid

  /** Method for removing some elements from a [[JointGridContainer]].
    *
    * @param jointGridContainer
    *   given container
    * @param nodes
    *   to be removed
    * @param transformers
    *   to be removed
    * @return
    *   a new [[JointGridContainer]]
    */
  def removeElements(
      jointGridContainer: JointGridContainer,
      nodes: Seq[NodeInput],
      transformers: Seq[TransformerInput]
  ): JointGridContainer = {
    val nodeIds = nodes.map { n => n.getUuid }
    val transformerIds = transformers.map { t => t.getUuid }

    // removing nodes and transformers
    val rawGridElements = new RawGridElements(
      jointGridContainer.getRawGrid
        .allEntitiesAsList()
        .asScala
        .filter { e =>
          val id = e.getUuid
          !nodeIds.contains(id) && !transformerIds.contains(id)
        }
        .asJava
    )

    new JointGridContainer(
      jointGridContainer.getGridName,
      rawGridElements,
      jointGridContainer.getSystemParticipants,
      jointGridContainer.getGraphics
    )
  }
}
