/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.guardian.run

import edu.ie3.datamodel.models.input.NodeInput
import edu.ie3.datamodel.models.input.connector._
import edu.ie3.datamodel.models.input.connector.`type`.{
  Transformer2WTypeInput,
  Transformer3WTypeInput
}
import edu.ie3.datamodel.models.input.container._
import edu.ie3.datamodel.utils.ContainerNodeUpdateUtil
import edu.ie3.datamodel.utils.validation.ValidationUtils
import edu.ie3.osmogrid.exception.GridException
import edu.ie3.osmogrid.guardian.run.SubGridHandling._
import edu.ie3.osmogrid.io.input.AssetInformation
import edu.ie3.osmogrid.io.output.{GridResult, OutputRequest}
import org.apache.pekko.actor.typed.ActorRef
import org.slf4j.Logger
import tech.units.indriya.ComparableQuantity

import java.util.UUID
import javax.measure.quantity.ElectricPotential
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

trait SubGridHandling {

  /** Handle incoming grid results.
    * @param lvData
    *   option for low voltage grids
    * @param mvData
    *   option for medium voltage grids
    * @param hvData
    *   option for high voltage grids
    * @param mvNodeChanges
    *   option for mv nodes that may have been changed
    * @param assetInformation
    *   information for assets
    * @param resultListener
    *   listeners for the resulting grid
    * @param msgAdapters
    *   some adapters for messages
    * @param log
    *   logger
    */
  protected def handleResults(
      lvData: Option[Seq[SubGridContainer]],
      mvData: Option[Seq[SubGridContainer]],
      hvData: Option[Seq[GridContainer]],
      mvNodeChanges: Option[Map[UUID, NodeInput]],
      assetInformation: Option[AssetInformation],
      resultListener: Seq[ActorRef[OutputRequest]],
      msgAdapters: MessageAdapters
  )(implicit log: Logger): Unit = {
    log.info("All requested grids successfully generated.")

    val allGrids: Seq[GridContainer] =
      processResults(lvData, mvData, hvData, mvNodeChanges, assetInformation)

    val jointGrid = if (allGrids.isEmpty) {
      throw GridException(
        s"Error during creating of joint grid container, because no grids were found."
      )
    } else {
      val gridName = allGrids(0).getGridName

      val grid = new JointGridContainer(
        gridName,
        new RawGridElements(allGrids.map(_.getRawGrid).asJava),
        new SystemParticipants(allGrids.map(_.getSystemParticipants).asJava),
        new GraphicElements(allGrids.map(_.getGraphics).asJava)
      )

      // maybe update some types
      assetInformation.map(info => updateTypes(grid, info)).getOrElse(grid)
    }

    // validate grid
    Try(ValidationUtils.check(jointGrid)) match {
      case Failure(exception) =>
        log.error(
          s"An exception occurred while validating the generated grid. Exception: $exception"
        )
      case Success(_) =>
    }

    // sending the finished grid to all interested listeners
    resultListener.foreach { listener =>
      listener ! GridResult(jointGrid)
    }
  }

  /** Method for processing results.
    *
    * @param lvData
    *   option for low voltage grids
    * @param mvData
    *   option for medium voltage grids
    * @param hvData
    *   option for high voltage grids
    * @param mvNodeChanges
    *   option for mv nodes that may have been changed
    * @param assetInformation
    *   information for assets
    * @return
    *   a sequence of [[SubGridContainer]]
    */
  protected def processResults(
      lvData: Option[Seq[SubGridContainer]],
      mvData: Option[Seq[SubGridContainer]],
      hvData: Option[Seq[GridContainer]],
      mvNodeChanges: Option[Map[UUID, NodeInput]],
      assetInformation: Option[AssetInformation]
  ): Seq[GridContainer] = {
    // assigning unique subgrid numbers to all given grids
    val nodeUpdateMap =
      assignSubNetNumbers(lvData, mvData, mvNodeChanges, hvData)

    // updating all grids with the updated nodes
    Seq(lvData, mvData, hvData).flatten.flatten.map(grid =>
      ContainerNodeUpdateUtil.updateGridWithNodes(grid, nodeUpdateMap.asJava)
    )
  }
}

object SubGridHandling {

  /** Method to assign subgrid numbers to nodes.
    * @param lvGrids
    *   option for low voltage grids
    * @param mvGrids
    *   option for medium voltage grids
    * @param mvNodeChanges
    *   option for medium voltage node changes
    * @param hvGrids
    *   option for high voltage grids
    * @return
    *   a map: old node to new node
    */
  private def assignSubNetNumbers(
      lvGrids: Option[Seq[GridContainer]],
      mvGrids: Option[Seq[GridContainer]],
      mvNodeChanges: Option[Map[UUID, NodeInput]],
      hvGrids: Option[Seq[GridContainer]]
  ): Map[NodeInput, NodeInput] = {
    val lv = lvGrids.map(grids => assignSubNetNumbers(grids, 1))
    val mvOffset = lv.map(_._2).getOrElse(1)

    val mv = mvGrids.map(grids => assignSubNetNumbers(grids, mvOffset))
    val hvOffset = mv.map(_._2).getOrElse(100)

    val mvCombined: Map[UUID, NodeInput] = (mv, mvNodeChanges) match {
      case (Some(gridNodeChanges), Some(nodeChanges)) =>
        // we need to combine multiple mv node changes
        val gridNodeMap = gridNodeChanges._1

        val commonKeys = nodeChanges.keySet.intersect(gridNodeMap.keySet)
        val allKeys = gridNodeChanges._1.keySet ++ nodeChanges.keySet

        allKeys.map { key =>
          if (commonKeys.contains(key)) {
            // update subnet information
            key -> nodeChanges(key)
              .copy()
              .subnet(
                gridNodeMap(key).getSubnet
              )
              .build()
          } else key -> gridNodeMap.getOrElse(key, nodeChanges(key))
        }.toMap

      case (Some(gridNodeChanges), None) =>
        // we only got mv grid containers
        gridNodeChanges._1
      case (None, Some(nodeChanges)) =>
        // we only got mv node changes
        nodeChanges

      case _ =>
        // if no mv nodes are present
        Map.empty
    }

    val hv = hvGrids.map { grids =>
      if (grids.size == 1 && grids(0).getGridName == "dummyHvGrid") {

        val hvNode: NodeInput = grids(0).getRawGrid.getNodes.asScala.toList
          .sortBy(
            _.getVoltLvl.getNominalVoltage.getValue.doubleValue()
          )
          .lastOption
          .getOrElse(throw GridException("No hv node found."))

        (
          Map(hvNode.getUuid -> hvNode.copy().subnet(hvOffset).build()),
          hvOffset + 2
        )
      } else {
        assignSubNetNumbers(grids, hvOffset)
      }
    }

    // maps with updated nodes
    val updateMap = lv.map(_._1).getOrElse(Map.empty) ++ mvCombined ++ hv
      .map(_._1)
      .getOrElse(Map.empty)

    val allNodes: Seq[NodeInput] = List(lvGrids, mvGrids, hvGrids).flatten
      .flatMap(_.flatMap(_.getRawGrid.getNodes.asScala))

    // final update map
    allNodes.map { node =>
      node -> updateMap.getOrElse(node.getUuid, node)
    }.toMap
  }

  /** Method to assign a sub grid number with an offset for nodes that are on a
    * higher voltage level. Nodes with a lower voltage level are not updated
    * @param subGrids
    *   with nodes
    * @param offset
    *   for nodes
    * @return
    *   a map of updated nodes and an offset for nodes with a higher voltage
    *   level
    */
  private def assignSubNetNumbers(
      subGrids: Seq[GridContainer],
      offset: Int
  ): (Map[UUID, NodeInput], Int) = {
    val higherNodesOffset = offset + subGrids.size + 1

    val nodeMap = subGrids.zipWithIndex.flatMap { case (container, i) =>
      val nodes = container.getRawGrid.getNodes.asScala.toSeq
      val voltage =
        findDominantVoltage(nodes.map(_.getVoltLvl.getNominalVoltage))

      nodes.map { node =>
        val nodeVoltage = node.getVoltLvl.getNominalVoltage

        val updatedNode = if (nodeVoltage.isEquivalentTo(voltage)) {
          node.copy().subnet(i + offset).build()
        } else if (nodeVoltage.isGreaterThan(voltage)) {
          node.copy().subnet(higherNodesOffset).build()
        } else node

        node.getUuid -> updatedNode
      }.toMap
    }.toMap

    (nodeMap, higherNodesOffset)
  }

  /** Method to find the dominant voltage for a given sequence of voltages
    * @param voltages
    *   given voltages
    * @return
    *   a [[ComparableQuantity]]
    */
  private def findDominantVoltage(
      voltages: Seq[ComparableQuantity[ElectricPotential]]
  ): ComparableQuantity[ElectricPotential] = voltages
    .groupBy(identity)
    .maxByOption(_._2.size)
    .map(_._1)
    .getOrElse(throw GridException("No voltage found."))

  /** Method to update some types.
    * @param jointGridContainer
    *   grid with entities
    * @param assetInformation
    *   information about types
    * @return
    *   updated grid
    */
  private def updateTypes(
      jointGridContainer: JointGridContainer,
      assetInformation: AssetInformation
  ): JointGridContainer = {

    val rawGridElements = jointGridContainer.getRawGrid

    // updating transformer2Ws
    val t2W = updateTransformer2Ws(
      rawGridElements.getTransformer2Ws.asScala.toSeq,
      assetInformation.transformerTypes
    ).fold(
      exception =>
        throw GridException(
          "Unable to update transformers.",
          exception
        ),
      identity
    )

    // updating transformer3Ws
    // TODO: Add transformer3WType update (at the moment no transformer3WType are available)
    val t3W = updateTransformer3Ws(
      rawGridElements.getTransformer3Ws.asScala.toSeq,
      assetInformation.transformer3WTypes
    ).fold(
      exception =>
        throw GridException(
          "Unable to update transformers.",
          exception
        ),
      identity
    )

    val rawGrid: RawGridElements = new RawGridElements(
      rawGridElements.getNodes,
      rawGridElements.getLines,
      t2W.toSet.asJava,
      t3W.toSet.asJava,
      rawGridElements.getSwitches,
      rawGridElements.getMeasurementUnits
    )

    new JointGridContainer(
      jointGridContainer.getGridName,
      rawGrid,
      jointGridContainer.getSystemParticipants,
      jointGridContainer.getGraphics
    )
  }

  /** Method for updating [[Transformer2WInput]]s.
    *
    * @param transformer2Ws
    *   sequence of transformers
    * @param typeInputs
    *   sequence of available [[Transformer2WTypeInput]]s
    * @return
    *   a sequence of updated [[Transformer2WInput]]s wrapped by a [[Success]]
    *   if all transformers are updated correctly
    */
  private def updateTransformer2Ws(
      transformer2Ws: Seq[Transformer2WInput],
      typeInputs: Seq[Transformer2WTypeInput]
  ): Try[Seq[Transformer2WInput]] = Try {
    transformer2Ws.map { originalTransformer =>
      val originalType = originalTransformer.getType
      val updated = originalTransformer.copy()

      // check if voltage ratings have changed
      val voltLvlA = originalTransformer.getNodeA.getVoltLvl.getNominalVoltage
      val voltLvlB = originalTransformer.getNodeB.getVoltLvl.getNominalVoltage

      if (
        originalType.getvRatedA() != voltLvlA || originalType
          .getvRatedB() != voltLvlB
      ) {
        typeInputs.find { t =>
          t.getvRatedA() == voltLvlA && t.getvRatedB() == voltLvlB
        } match {
          case Some(value) => updated.`type`(value)
          case None =>
            throw GridException(
              s"No transformer2WType for voltage levels $voltLvlA and $voltLvlB found!"
            )
        }
      }

      updated.build()
    }
  }

  /** Method for updating [[Transformer3WInput]]s.
    *
    * @param transformer3Ws
    *   sequence of transformers
    * @param typeInputs
    *   sequence of available [[Transformer3WTypeInput]]s
    * @return
    *   a sequence of updated [[Transformer3WInput]]s wrapped by a [[Success]]
    *   if all transformers are updated correctly
    */
  private def updateTransformer3Ws(
      transformer3Ws: Seq[Transformer3WInput],
      typeInputs: Seq[Transformer3WTypeInput]
  ): Try[Seq[Transformer3WInput]] = Try {
    transformer3Ws.map { originalTransformer =>
      val originalType = originalTransformer.getType
      val updated = originalTransformer.copy()

      // check if voltage ratings have changed
      val voltLvlA = originalTransformer.getNodeA.getVoltLvl.getNominalVoltage
      val voltLvlB = originalTransformer.getNodeB.getVoltLvl.getNominalVoltage
      val voltLvlC = originalTransformer.getNodeC.getVoltLvl.getNominalVoltage

      if (
        originalType.getvRatedA() != voltLvlA || originalType
          .getvRatedB() != voltLvlB || originalType.getvRatedC() != voltLvlC
      ) {
        typeInputs.find { t =>
          t.getvRatedA() == voltLvlA && t.getvRatedB() == voltLvlB && t
            .getvRatedC() == voltLvlC
        } match {
          case Some(value) => updated.`type`(value)
          case None =>
            throw GridException(
              s"No transformer3WType for voltage levels $voltLvlA, $voltLvlB and $voltLvlC found!"
            )
        }
      }

      updated.build()
    }
  }
}
