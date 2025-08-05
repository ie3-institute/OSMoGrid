/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package utils

import edu.ie3.datamodel.models.input.container.*
import edu.ie3.datamodel.models.input.{AssetInput, NodeInput}
import edu.ie3.datamodel.models.voltagelevels.CommonVoltageLevel
import edu.ie3.osmogrid.cfg.OsmoGridConfig.Voltage
import edu.ie3.osmogrid.guardian.run.RunGuardian
import tech.units.indriya.ComparableQuantity

import javax.measure.quantity.ElectricPotential
import scala.jdk.CollectionConverters.*

object GridContainerUtils {
  private val cfg: Voltage = RunGuardian.getVoltageConfig

  /** Method for retrieving all nodes of a given voltage level from a sequence
    * of [[SubGridContainer]].
    *
    * @param grids
    *   given sub or inferior grids
    * @return
    *   all found nodes
    */
  def filterForVoltageLvl(
      grids: Seq[SubGridContainer],
      voltageLvL: CommonVoltageLevel,
  ): Seq[NodeInput] = {
    grids.flatMap { grid =>
      val preDominantVoltageLvl =
        grid.getPredominantVoltageLevel.getNominalVoltage

      // if Voltage is higher then preDominantVoltage than we must take the nodes from transformer
      if (voltageLvL.getNominalVoltage.isGreaterThan(preDominantVoltageLvl)) {
        // Collect nodes from 2W transformers
        val nodes2WTransformers = grid.getRawGrid.getTransformer2Ws.asScala
          .flatMap(transformer => transformer.allNodes().asScala)
          .filter(node => voltageLvL.equals(node.getVoltLvl))

        // Collect nodes from 3W transformers
        val nodes3WTransformers = grid.getRawGrid.getTransformer3Ws.asScala
          .flatMap(transformer => transformer.allNodes().asScala)
          .filter(node => voltageLvL.equals(node.getVoltLvl))

        // Combine the results from both transformers
        nodes2WTransformers ++ nodes3WTransformers

      } else if (
        voltageLvL.getNominalVoltage.isLessThanOrEqualTo(preDominantVoltageLvl)
      ) {
        // Collect all nodes directly from the raw grid
        grid.getRawGrid.getNodes.asScala.filter(node =>
          voltageLvL.equals(node.getVoltLvl)
        )

      } else {
        Seq.empty[NodeInput] // Return an empty sequence if no conditions match.
      }
    }
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
      subGrids: Seq[SubGridContainer],
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

  /** Method to create a dummy grid from the given [[AssetInput]]s.
    * @param assets
    *   given assets
    * @param gridName
    *   name of the grid
    * @return
    *   a dummy grid
    */
  def from(
      assets: Seq[AssetInput],
      gridName: String = "dummy grid",
  ): JointGridContainer = {
    new JointGridContainer(
      gridName,
      new RawGridElements(assets.asJava),
      new SystemParticipants(List.empty[SystemParticipants].asJava),
      new GraphicElements(List.empty[GraphicElements].asJava),
    )
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
      containerB: JointGridContainer,
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
      s"Joint container of the two grids ${containerA.getGridName} and ${containerB.getGridName}",
      new RawGridElements(rawGridElements.asJava),
      new SystemParticipants(participants.asJava),
      new GraphicElements(graphicElements.asJava),
    )
  }
}
