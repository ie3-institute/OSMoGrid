/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.guardian.run

import edu.ie3.datamodel.graph.SubGridTopologyGraph
import edu.ie3.datamodel.models.input.connector._
import edu.ie3.datamodel.models.input.connector.`type`.{
  Transformer2WTypeInput,
  Transformer3WTypeInput
}
import edu.ie3.datamodel.models.input.container._
import edu.ie3.datamodel.models.input.{AssetInput, NodeInput}
import edu.ie3.datamodel.utils.{ContainerNodeUpdateUtil, ContainerUtils}
import edu.ie3.osmogrid.exception.GridException
import edu.ie3.osmogrid.guardian.run.SubGridHandling._
import edu.ie3.osmogrid.io.input.AssetInformation
import edu.ie3.osmogrid.io.output.{GridResult, OutputRequest}
import org.apache.pekko.actor.typed.ActorRef
import org.slf4j.Logger

import java.util.UUID
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
    * @param assetInformation
    *   for updating types
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
      hvData: Option[Seq[SubGridContainer]],
      assetInformation: AssetInformation,
      resultListener: Seq[ActorRef[OutputRequest]],
      msgAdapters: MessageAdapters
  )(implicit log: Logger): Unit = {
    log.info("All requested grids successfully generated.")

    val combinedGrids = processResults(lvData, mvData, hvData, assetInformation)

    val jointGrid = combinedGrids.getOrElse(
      throw GridException(
        s"Error during creating of joint grid container, because no grids were found."
      )
    )

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
    * @param assetInformation
    *   for updating types
    * @return
    *   a sequence of [[SubGridContainer]]
    */
  protected def processResults(
      lvData: Option[Seq[SubGridContainer]],
      mvData: Option[Seq[SubGridContainer]],
      hvData: Option[Seq[SubGridContainer]],
      assetInformation: AssetInformation
  ): Option[JointGridContainer] = {
    // combining lv grids
    val lvOption: Option[(JointGridContainer, Int)] = lvData.map { grids =>
      val changedNodes = assignSubgridNumbers(grids)
      val jointGridContainer = combineSafe(grids, 2)

      (updateGrid(changedNodes, jointGridContainer), grids.size + 1)
    }

    // combining lv and mv grids
    val lvMvOption: Option[(JointGridContainer, Int)] =
      (lvOption, mvData) match {
        case (Some((jointLvGrids, offset)), Some(mvGrids)) =>
          val changedNodes = assignSubgridNumbers(mvGrids, offset)
          val jointMvGrids = combineSafe(mvGrids, offset)

          Some(
            updateAndCombine(
              "Combined_mv_lv_grids",
              jointLvGrids,
              jointMvGrids,
              changedNodes
            ),
            offset + mvGrids.size + 1
          )
        case (None, Some(mvGrids)) =>
          val changedNodes = assignSubgridNumbers(mvGrids)
          val jointMvGrids = combineSafe(mvGrids, 1)

          Some((updateGrid(changedNodes, jointMvGrids), mvGrids.size + 1))
        case (Some(jointGrids), _) =>
          // if no mv grids were provided
          Some(jointGrids)
        case (_, _) =>
          None
      }

    // combining lv, mv and hv grids
    val lvMvHvOption: Option[(JointGridContainer, Int)] =
      (lvMvOption, hvData) match {
        case (Some((combinedLvMv, offset)), Some(hvGrids)) =>
          val changedNodes = assignSubgridNumbers(hvGrids, offset)
          val jointHvGrids = combineSafe(hvGrids, offset)

          Some(
            updateAndCombine(
              "Combined_hv_mv_lv_grids",
              combinedLvMv,
              jointHvGrids,
              changedNodes
            ),
            offset + hvGrids.size + 1
          )
        case (None, Some(hvGrids)) =>
          val changedNodes = assignSubgridNumbers(hvGrids)
          val jointHvGrids = combineSafe(hvGrids, 1)

          Some(updateGrid(changedNodes, jointHvGrids), hvGrids.size + 1)
        case (Some(jointGrids), _) =>
          // if no hv grids were provided
          Some(jointGrids)
        case (_, _) =>
          None
      }

    lvMvHvOption.map { case (grid, _) =>
      // check and update some assets
      val rawGridElements = grid.getRawGrid

      val transformer2Ws = updateTransformer2Ws(
        rawGridElements.getTransformer2Ws.asScala.toSeq,
        assetInformation.transformerTypes
      ).fold(
        t => throw t,
        seq => seq
      )
      val transformer3Ws = updateTransformer3Ws(
        rawGridElements.getTransformer3Ws.asScala.toSeq,
        assetInformation.transformer3WTypes
      ).fold(
        t => throw t,
        seq => seq
      )

      val assets: Seq[AssetInput] = Seq(
        rawGridElements.getNodes.asScala,
        rawGridElements.getLines.asScala,
        transformer2Ws,
        transformer3Ws,
        rawGridElements.getSwitches.asScala,
        rawGridElements.getMeasurementUnits.asScala
      ).flatten

      new JointGridContainer(
        grid.getGridName,
        new RawGridElements(assets.asJava),
        grid.getSystemParticipants,
        grid.getGraphics
      )
    }
  }
}

object SubGridHandling {

  /** Updates the nodes of the given sub grids by assigning a unique subgrid
    * number.
    * @param subnets
    *   to update
    * @param offset
    *   number for nodes with higher volt levels
    * @return
    *   a map of updated node
    */
  private def assignSubgridNumbers(
      subnets: Seq[SubGridContainer],
      offset: Int = 1
  ): Map[UUID, NodeInput] = {
    val size = subnets.size + offset

    subnets.zipWithIndex.flatMap { case (subgrid, subgridNumber) =>
      val voltLvl = subgrid.getPredominantVoltageLevel.getNominalVoltage
      val nr = subgridNumber + offset
      val allNodes = subgrid.getRawGrid.getNodes.asScala

      allNodes.flatMap { node =>
        if (node.getVoltLvl.getNominalVoltage.isEquivalentTo(voltLvl)) {
          Some(node.getUuid -> node.copy().subnet(nr).build())
        } else if (node.getVoltLvl.getNominalVoltage.isGreaterThan(voltLvl)) {
          Some(node.getUuid -> node.copy().subnet(size).build())
        } else {
          None
        }
      }.toMap
    }.toMap
  }

  /** Updates the nodes and all connected assets of a given grid.
    * @param changedNodes
    *   map of changed nodes
    * @param grid
    *   to be updated
    * @return
    *   an updated grid
    */
  private def updateGrid[T <: GridContainer](
      changedNodes: Map[UUID, NodeInput],
      grid: T
  ): T = {
    val allNodes = grid.getRawGrid.getNodes.asScala.map { node =>
      node.getUuid -> node
    }.toMap

    val updateMap = changedNodes.map { case (uuid, node) =>
      allNodes(uuid) -> node
    }

    ContainerNodeUpdateUtil
      .updateGridWithNodes(grid, updateMap.asJava)
      .asInstanceOf[T]
  }

  /** Method for combining two [[JointGridContainer]].
    *
    * @param gridName
    *   name of the new grid
    * @param grid1
    *   first grid
    * @param grid2
    *   second grid
    * @param changedNodes
    *   node updates that are applied before combining the two grids
    * @return
    *   a new joint grid
    */
  private def updateAndCombine(
      gridName: String,
      grid1: JointGridContainer,
      grid2: JointGridContainer,
      changedNodes: Map[UUID, NodeInput]
  ): JointGridContainer = {
    val updatedGrid1 = updateGrid(changedNodes, grid1)
    val updatedGrid2 = updateGrid(changedNodes, grid2)

    new JointGridContainer(
      gridName,
      new RawGridElements(
        List(updatedGrid1.getRawGrid, updatedGrid2.getRawGrid).asJava
      ),
      new SystemParticipants(
        List(
          updatedGrid1.getSystemParticipants,
          updatedGrid2.getSystemParticipants
        ).asJava
      ),
      new GraphicElements(
        List(updatedGrid1.getGraphics, updatedGrid2.getGraphics).asJava
      )
    )
  }

  /** Used to prevent loops in [[SubGridTopologyGraph]].
    * @param grids
    *   to combine safely
    * @param nr
    *   of the grid
    * @return
    *   a [[SubGridContainer]]
    */
  private def combineSafe(
      grids: Seq[SubGridContainer],
      nr: Int
  ): JointGridContainer = {
    val changedNodes = assignSubgridNumbers(grids)

    if (grids.size == 1) {
      // build joint grid directly
      val grid = updateGrid(changedNodes, grids(0))

      new JointGridContainer(
        grid.getGridName,
        grid.getRawGrid,
        grid.getSystemParticipants,
        grid.getGraphics
      )
    } else {
      ContainerUtils.combineToJointGrid(grids.asJava)
    }
  }

  /** Method for updating the type of [[Transformer2WInput]]s.
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
  ): Try[Seq[Transformer2WInput]] = Try(
    transformer2Ws
      .map { originalTransformer =>
        val nodeA = originalTransformer.getNodeA
        val nodeB = originalTransformer.getNodeB
        val originalType = originalTransformer.getType

        // check if voltage ratings have changed
        val voltLvlA = nodeA.getVoltLvl.getNominalVoltage
        val voltLvlB = nodeB.getVoltLvl.getNominalVoltage

        if (
          !originalType.getvRatedA().isEquivalentTo(voltLvlA) || !originalType
            .getvRatedB()
            .isEquivalentTo(voltLvlB)
        ) {

          // find any suitable transformer type
          val typeOption = typeInputs
            .filter { t =>
              t.getvRatedA().isEquivalentTo(voltLvlA) && t
                .getvRatedB()
                .isEquivalentTo(voltLvlB)
            }
            .sortBy(_.getsRated())
            .lastOption

          typeOption
            .map { t => Success(originalTransformer.copy().`type`(t).build()) }
            .getOrElse(
              Failure(
                GridException(
                  s"No transformer2WType for voltage levels $voltLvlA and $voltLvlB found!"
                )
              )
            )
        } else {
          Success(originalTransformer)
        }
      }
      .map(_.fold(t => throw t, seq => seq))
  )

  /** Method for updating the type of [[Transformer3WInput]]s.
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
  ): Try[Seq[Transformer3WInput]] = Try(
    transformer3Ws
      .map { originalTransformer =>
        val nodeA = originalTransformer.getNodeA
        val nodeB = originalTransformer.getNodeB
        val nodeC = originalTransformer.getNodeC
        val originalType = originalTransformer.getType

        // check if voltage ratings have changed
        val voltLvlA = nodeA.getVoltLvl.getNominalVoltage
        val voltLvlB = nodeB.getVoltLvl.getNominalVoltage
        val voltLvlC = nodeC.getVoltLvl.getNominalVoltage

        if (
          !originalType.getvRatedA().isEquivalentTo(voltLvlA) || !originalType
            .getvRatedB()
            .isEquivalentTo(voltLvlB) || !originalType
            .getvRatedC()
            .isEquivalentTo(voltLvlC)
        ) {
          // find any suitable transformer type
          val typeOption = typeInputs
            .filter { t =>
              t.getvRatedA().isEquivalentTo(voltLvlA) && t
                .getvRatedB()
                .isEquivalentTo(voltLvlB)
            }
            .sortBy { t =>
              t.getsRatedA().isGreaterThanOrEqualTo(originalType.getsRatedA())
              t.getsRatedB().isGreaterThanOrEqualTo(originalType.getsRatedB())
              t.getsRatedC().isGreaterThanOrEqualTo(originalType.getsRatedC())
            }
            .lastOption

          typeOption
            .map { t => Success(originalTransformer.copy().`type`(t).build()) }
            .getOrElse(
              Failure(
                GridException(
                  s"No transformer3WType for voltage levels $voltLvlA and $voltLvlB and $voltLvlC found!"
                )
              )
            )
        } else {
          Success(originalTransformer)
        }
      }
      .map(_.fold(t => throw t, seq => seq))
  )
}
