/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package utils

import edu.ie3.datamodel.models.input.NodeInput
import edu.ie3.datamodel.models.input.container._
import edu.ie3.datamodel.models.voltagelevels.VoltageLevel
import edu.ie3.osmogrid.cfg.OsmoGridConfig.Voltage
import edu.ie3.osmogrid.guardian.run.RunGuardian
import tech.units.indriya.ComparableQuantity

import javax.measure.quantity.ElectricPotential
import scala.jdk.CollectionConverters._

object GridContainerUtils {
  private val cfg: Voltage = RunGuardian.get

  /** Method for retrieving all mv nodes from a sequence of lv
    * [[SubGridContainer]].
    * @param lvGrids
    *   given sub grids
    * @return
    *   all found nodes
    */
  def filterLv(
      lvGrids: Seq[SubGridContainer]
  ): Seq[NodeInput] = {
    val mvVoltLvl = VoltageUtils.parse(cfg.mv)
    /* gets all mv-lv nodes */
    getNodes(mvVoltLvl, lvGrids)
  }

  /** Method for retrieving all mv nodes from a sequence of hv
    * [[SubGridContainer]].
    * @param hvGrids
    *   given sub grids
    * @return
    *   all found nodes
    */
  def filterHv(
      hvGrids: Seq[SubGridContainer]
  ): Seq[NodeInput] = {
    val mvVoltLvl = VoltageUtils.parse(cfg.mv)
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
      voltageLevels: List[ComparableQuantity[ElectricPotential]],
      subGrids: Seq[SubGridContainer]
  ): Seq[NodeInput] = {
    subGrids.flatMap(subgrid => {
      /* finds all transformer in the given subgrid */
      val rawGridElements = subgrid.getRawGrid
      val transformers =
        rawGridElements.getTransformer2Ws.asScala ++ rawGridElements.getTransformer3Ws.asScala

      transformers
        .flatMap(transformer =>
          /* gets all nodes connected to a given transformer and returns all nodes that have a mv voltage level */
          transformer
            .allNodes()
            .asScala
            .filter(node =>
              voltageLevels.contains(node.getVoltLvl.getNominalVoltage)
            )
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
    // combining raw grid elements
    val rawGridElements = containerA.getRawGrid
      .allEntitiesAsList()
      .asScala :++ containerB.getRawGrid.allEntitiesAsList().asScala

    // combining system participants
    val participants = containerA.getSystemParticipants
      .allEntitiesAsList()
      .asScala :++ containerB.getSystemParticipants.allEntitiesAsList().asScala

    // combining graphic elements
    val graphicElements = containerA.getGraphics
      .allEntitiesAsList()
      .asScala :++ containerB.getGraphics.allEntitiesAsList().asScala

    new JointGridContainer(
      containerA.getGridName,
      new RawGridElements(rawGridElements.asJava),
      new SystemParticipants(participants.asJava),
      new GraphicElements(graphicElements.asJava)
    )
  }
}
