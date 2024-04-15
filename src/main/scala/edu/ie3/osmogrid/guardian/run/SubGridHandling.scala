/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.guardian.run

import edu.ie3.datamodel.graph.SubGridTopologyGraph
import edu.ie3.datamodel.models.input.{AssetInput, NodeInput}
import edu.ie3.datamodel.models.input.connector._
import edu.ie3.datamodel.models.input.connector.`type`.{
  Transformer2WTypeInput,
  Transformer3WTypeInput
}
import edu.ie3.datamodel.models.input.container._
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
      val jointGridContainer = combineSafe(grids, changedNodes)

      (jointGridContainer, grids.size + 1)
    }

    // combining lv and mv grids
    val lvMvOption: Option[(JointGridContainer, Int)] =
      updateAndCombine(lvOption, mvData)

    // combining lv, mv and hv grids
    val lvMvHvOption: Option[(JointGridContainer, Int)] =
      updateAndCombine(lvMvOption, hvData)

    lvMvHvOption.map { case (grid, _) =>
      // check and update some assets
      val rawGridElements = grid.getRawGrid

      val transformer2Ws = updateTransformer2Ws(
        rawGridElements.getTransformer2Ws.asScala.toSeq,
        assetInformation.transformerTypes
      ).fold(
        t => throw t,
        _.toSet
      )
      val transformer3Ws = updateTransformer3Ws(
        rawGridElements.getTransformer3Ws.asScala.toSeq,
        assetInformation.transformer3WTypes
      ).fold(
        t => throw t,
        _.toSet
      )

      new JointGridContainer(
        grid.getGridName,
        new RawGridElements(
          rawGridElements.getNodes,
          rawGridElements.getLines,
          transformer2Ws.asJava,
          transformer3Ws.asJava,
          rawGridElements.getSwitches,
          rawGridElements.getMeasurementUnits
        ),
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
    *   offset for the subnet number (default: 1)
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
    *
    * @param grid
    *   to be updated
    * @param changedNodes
    *   map of changed nodes
    * @return
    *   an updated grid
    */
  private def updateGrid[T <: GridContainer](
      grid: T,
      changedNodes: Map[UUID, NodeInput]
  ): T = {
    val allNodes = grid.getRawGrid.getNodes.asScala.map { node =>
      node.getUuid -> node
    }.toMap

    val updateMap = changedNodes.flatMap { case (uuid, node) =>
      allNodes.get(uuid).map(old => old -> node)
    }

    ContainerNodeUpdateUtil.updateGridWithNodes(grid, updateMap.asJava) match {
      case container: SubGridContainer =>
        val nr = container.getRawGrid.getNodes.asScala
          .map(_.getSubnet)
          .minOption
          .getOrElse(-1)

        new SubGridContainer(
          container.getGridName,
          nr,
          container.getRawGrid,
          container.getSystemParticipants,
          container.getGraphics
        ).asInstanceOf[T]
      case container: T => container
    }
  }

  /** Method for combining two [[JointGridContainer]].
    *
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
      grid1: JointGridContainer,
      grid2: JointGridContainer,
      changedNodes: Map[UUID, NodeInput]
  ): JointGridContainer = {
    val updatedGrid1 = updateGrid(grid1, changedNodes)
    val updatedGrid2 = updateGrid(grid2, changedNodes)

    new JointGridContainer(
      "Combined_grids",
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

  private def updateAndCombine(
      jointGridOption: Option[(JointGridContainer, Int)],
      subgridOptions: Option[Seq[SubGridContainer]]
  ): Option[(JointGridContainer, Int)] =
    (jointGridOption, subgridOptions) match {
      case (Some((jointGrids, offset)), Some(subGrids)) =>
        val changedNodes = assignSubgridNumbers(subGrids, offset)
        val jointSubGrids = combineSafe(subGrids, changedNodes)

        Some(
          updateAndCombine(
            jointGrids,
            jointSubGrids,
            changedNodes
          ),
          offset + subGrids.size + 1
        )
      case (None, Some(mvGrids)) =>
        val changedNodes = assignSubgridNumbers(mvGrids)
        val jointMvGrids = combineSafe(mvGrids, changedNodes)

        Some((jointMvGrids, mvGrids.size + 1))
      case (Some(jointGrids), _) =>
        // if no sub grids were provided
        Some(jointGrids)
      case (_, _) =>
        None
    }

  /** Used to prevent loops in [[SubGridTopologyGraph]].
    * @param grids
    *   to combine safely
    * @param changedNodes
    *   map of changed nodes
    * @return
    *   a [[SubGridContainer]]
    */
  private def combineSafe(
      grids: Seq[SubGridContainer],
      changedNodes: Map[UUID, NodeInput]
  ): JointGridContainer = {

    if (grids.size == 1) {
      // build joint grid directly
      val grid = updateGrid(grids(0), changedNodes)

      new JointGridContainer(
        grid.getGridName,
        grid.getRawGrid,
        grid.getSystemParticipants,
        grid.getGraphics
      )
    } else {
      val updatedGrids = grids.map { grid => updateGrid(grid, changedNodes) }

      val higherVoltageNodes = updatedGrids.flatMap { grid =>
        val nr = grid.getSubnet
        grid.getRawGrid.getNodes.asScala.filter(_.getSubnet > nr)
      }

      val subGridContainer = Seq(
        new SubGridContainer(
          updatedGrids(0).getGridName,
          higherVoltageNodes.map(_.getSubnet).toSeq(0),
          new RawGridElements(
            higherVoltageNodes.map(_.asInstanceOf[AssetInput]).asJava
          ),
          new SystemParticipants(Set.empty[SystemParticipants].asJava),
          new GraphicElements(Set.empty[GraphicElements].asJava)
        )
      )

      ContainerUtils.combineToJointGrid(
        (updatedGrids ++ subGridContainer).asJava
      )
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
