/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.guardian.run

import edu.ie3.datamodel.models.input.connector._
import edu.ie3.datamodel.models.input.connector.`type`.{
  Transformer2WTypeInput,
  Transformer3WTypeInput
}
import edu.ie3.datamodel.models.input.container._
import edu.ie3.datamodel.models.input.graphics.{
  LineGraphicInput,
  NodeGraphicInput
}
import edu.ie3.datamodel.models.input.system._
import edu.ie3.datamodel.models.input.{MeasurementUnitInput, NodeInput}
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
    * @param toBeUpdated
    *   option for [[RawGridElements]] that needs to be removed
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
      toBeUpdated: Option[(Seq[NodeInput], AssetInformation)],
      resultListener: Seq[ActorRef[OutputRequest]],
      msgAdapters: MessageAdapters
  )(implicit log: Logger): Unit = {
    log.info("All requested grids successfully generated.")

    val allGrids: Seq[GridContainer] =
      processResults(lvData, mvData, hvData, toBeUpdated)

    val jointGrid = if (allGrids.isEmpty) {
      throw GridException(
        s"Error during creating of joint grid container, because no grids were found."
      )
    } else {
      val gridName = allGrids(0).getGridName

      new JointGridContainer(
        gridName,
        new RawGridElements(allGrids.map(_.getRawGrid).asJava),
        new SystemParticipants(allGrids.map(_.getSystemParticipants).asJava),
        new GraphicElements(allGrids.map(_.getGraphics).asJava)
      )
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
    * @param toBeUpdated
    *   option for [[RawGridElements]] that needs to be removed
    * @return
    *   a sequence of [[SubGridContainer]]
    */
  protected def processResults(
      lvData: Option[Seq[SubGridContainer]],
      mvData: Option[Seq[SubGridContainer]],
      hvData: Option[Seq[GridContainer]],
      toBeUpdated: Option[(Seq[NodeInput], AssetInformation)]
  ): Seq[GridContainer] = {
    // updating lv grids
    val lvGrids: Option[Seq[SubGridContainer]] = lvData.map { grids =>
      // assigning some subnet numbers
      val updated = assignSubnetNumbers(grids).fold(f => throw f, identity)

      // removing some elements is defined
      toBeUpdated
        .map { r => updateContainer(updated, r._1, r._2) }
        .getOrElse(updated)
    }

    // updating hv grids
    val hvGrids: Option[Seq[GridContainer]] = hvData.map { grids =>
      if (grids.nonEmpty && grids(0).isInstanceOf[SubGridContainer]) {
        // assigning some subnet numbers
        val updated = assignSubnetNumbers(
          grids.map(_.asInstanceOf[SubGridContainer])
        ).fold(f => throw f, identity)

        // removing some elements is defined
        toBeUpdated
          .map { r => updateContainer(updated, r._1, r._2) }
          .getOrElse(updated)
      } else {
        // do nothing if we receive a dummy hv grid
        grids
      }
    }

    Seq(lvGrids, mvData, hvGrids).flatMap { s => s.toSeq.flatten }
  }
}

object SubGridHandling {

  /** Method for updating [[SubGridContainer]].
    * @param grids
    *   to update
    * @param nodes
    *   that have changed
    * @param assetInformation
    *   information for assets
    * @return
    *   an sequence of updated [[SubGridContainer]]
    */
  private def updateContainer(
      grids: Seq[SubGridContainer],
      nodes: Seq[NodeInput],
      assetInformation: AssetInformation
  ): Seq[SubGridContainer] = {
    grids.map { grid =>
      val updatedNodes: Map[UUID, NodeInput] = nodes.map { n =>
        n.getUuid -> n
      }.toMap

      val nodeMapping: Map[UUID, NodeInput] =
        grid.getRawGrid.getNodes.asScala.map { n =>
          val id = n.getUuid
          id -> updatedNodes.getOrElse(id, n)
        }.toMap

      val rawGridElements = grid.getRawGrid

      // updating transformer2Ws
      val t2W = updateTransformer2Ws(
        rawGridElements.getTransformer2Ws.asScala.toSeq,
        nodeMapping,
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
        nodeMapping,
        Seq.empty
      ).fold(
        exception =>
          throw GridException(
            "Unable to update transformers.",
            exception
          ),
        identity
      )

      val rawGrid: RawGridElements = new RawGridElements(
        nodeMapping.values.toSet.asJava,
        rawGridElements.getLines,
        t2W.toSet.asJava,
        t3W.toSet.asJava,
        rawGridElements.getSwitches,
        rawGridElements.getMeasurementUnits
      )

      new SubGridContainer(
        grid.getGridName,
        grid.getSubnet,
        rawGrid,
        grid.getSystemParticipants,
        grid.getGraphics
      )
    }
  }

  private def assignSubnetNumbers(
      subnets: Seq[SubGridContainer]
  ): Try[Seq[SubGridContainer]] = Try {
    subnets.zipWithIndex.map { case (subGrid, subnetNumber) =>
      assignSubnetNumber(subGrid, subnetNumber + 1) match {
        case Success(value) => value
        case Failure(exception) =>
          throw GridException(
            s"Unable to update subgrid container '$subGrid'.",
            exception
          )
      }
    }
  }

  /** Assign a certain subnet number to the [[SubGridContainer]]. This is done
    * by iterating through all elements and assigning the subnet number to nodes
    * and updating the element reference, respectively.
    *
    * @param subGrid
    *   The sub grid container to be updated
    * @param subnetNumber
    *   The sub net number to be assigned
    * @return
    *   The updated [[SubGridContainer]]
    */
  private def assignSubnetNumber(
      subGrid: SubGridContainer,
      subnetNumber: Int
  ): Try[SubGridContainer] = Try {
    val nodes = updateNodes(
      subGrid.getRawGrid.getNodes.asScala.toSeq,
      subnetNumber
    )

    val rawGrid = updateRawGrid(subGrid.getRawGrid, nodes).fold(
      exception =>
        throw GridException(
          "Unable to update raw grid structure.",
          exception
        ),
      identity
    )

    val systemParticipants =
      updateSystemParticipants(subGrid.getSystemParticipants, nodes).fold(
        exception =>
          throw GridException(
            "Unable to update system participants.",
            exception
          ),
        identity
      )

    val graphics = updateGraphics(
      subGrid.getGraphics,
      nodes,
      rawGrid.getLines.asScala.map(line => line.getUuid -> line).toMap
    ).fold(
      exception =>
        throw GridException(
          "Unable to update graphic elements.",
          exception
        ),
      identity
    )

    new SubGridContainer(
      subGrid.getGridName,
      subnetNumber,
      rawGrid,
      systemParticipants,
      graphics
    )
  }

  private def updateNodes(
      nodes: Seq[NodeInput],
      subnetNumber: Int
  ): Map[UUID, NodeInput] = nodes
    .map(node => node.getUuid -> node.copy().subnet(subnetNumber).build())
    .toMap

  /** Update the raw grid elements
    *
    * @param rawGrid
    *   Container of raw grids
    * @param nodeMapping
    *   Mapping from node [[UUID]] to updated [[NodeInput]]
    * @return
    *   Container for grid elements with updated node references
    */
  private def updateRawGrid(
      rawGrid: RawGridElements,
      nodeMapping: Map[UUID, NodeInput]
  ): Try[RawGridElements] = Try {
    val lines =
      updateNodeReferences(
        rawGrid.getLines.asScala.toSeq,
        nodeMapping,
        (line: LineInput, nodeA: NodeInput, nodeB: NodeInput) =>
          line.copy().nodeA(nodeA).nodeB(nodeB).build()
      ).fold(
        exception =>
          throw GridException(
            "Unable to update node references of lines.",
            exception
          ),
        identity
      )

    // transformer top node is included within the node set
    val transformers2w =
      updateNodeReferences(
        rawGrid.getTransformer2Ws.asScala.toSeq,
        nodeMapping,
        (transformer: Transformer2WInput, nodeA: NodeInput, nodeB: NodeInput) =>
          transformer.copy().nodeA(nodeA).nodeB(nodeB).build()
      ).fold(
        exception =>
          throw GridException(
            "Unable to update node references of two winding transformers.",
            exception
          ),
        identity
      )

    // transformer top nodes are included within the node set
    val transformers3w =
      updateNodeReferences(
        rawGrid.getTransformer3Ws.asScala.toSeq,
        nodeMapping
      ).fold(
        exception =>
          throw GridException(
            "Unable to update node references of three winding transformers.",
            exception
          ),
        identity
      )

    val switches =
      updateNodeReferences(
        rawGrid.getSwitches.asScala.toSeq,
        nodeMapping,
        (swtch: SwitchInput, nodeA: NodeInput, nodeB: NodeInput) =>
          swtch.copy().nodeA(nodeA).nodeB(nodeB).build()
      ).fold(
        exception =>
          throw GridException(
            "Unable to update node references of switches.",
            exception
          ),
        identity
      )

    val measurements =
      updateMeasurements(
        rawGrid.getMeasurementUnits.asScala.toSeq,
        nodeMapping
      ).fold(
        exception =>
          throw GridException(
            "Unable to update node references of measurements.",
            exception
          ),
        identity
      )

    new RawGridElements(
      nodeMapping.values.toSet.asJava,
      lines.toSet.asJava,
      transformers2w.toSet.asJava,
      transformers3w.toSet.asJava,
      switches.toSet.asJava,
      measurements.toSet.asJava
    )
  }

  /** Update the node references in two port connectors
    *
    * @param connectors
    *   Collection of [[ConnectorInput]] to update
    * @param nodeMapping
    *   Mapping from node [[UUID]] to updated nodes
    * @param updateFunc
    *   Function, that does the update for the connector
    * @tparam T
    *   Type of the connector to update
    * @return
    *   Two-port connector with updated node references
    */
  private def updateNodeReferences[T <: ConnectorInput](
      connectors: Seq[T],
      nodeMapping: Map[UUID, NodeInput],
      updateFunc: (T, NodeInput, NodeInput) => T
  ): Try[Seq[T]] = Try {
    connectors.map { originalConnector =>
      nodeMapping
        .get(originalConnector.getNodeA.getUuid)
        .zip(nodeMapping.get(originalConnector.getNodeB.getUuid)) match {
        case Some((nodeA, nodeB)) =>
          updateFunc(originalConnector, nodeA, nodeB)
        case None =>
          throw GridException(
            s"Unable to determine new nodes for connector '$originalConnector'."
          )
      }
    }
  }

  /** Update the node references in [[Transformer3WInput]]s
    *
    * @param connectors
    *   Collection of [[Transformer3WInput]] to update
    * @param nodeMapping
    *   Mapping from node [[UUID]] to updated nodes
    * @return
    *   [[Transformer3WInput]]s with updated node references
    */
  private def updateNodeReferences(
      connectors: Seq[Transformer3WInput],
      nodeMapping: Map[UUID, NodeInput]
  ): Try[Seq[Transformer3WInput]] = Try {
    connectors.map { originalTransformer =>
      nodeMapping
        .get(originalTransformer.getNodeA.getUuid)
        .zip(nodeMapping.get(originalTransformer.getNodeB.getUuid))
        .zip(nodeMapping.get(originalTransformer.getNodeC.getUuid)) match {
        case Some(((nodeA, nodeB), nodeC)) =>
          originalTransformer
            .copy()
            .nodeA(nodeA)
            .nodeB(nodeB)
            .nodeC(nodeC)
            .build()
        case None =>
          throw GridException(
            s"Unable to determine new nodes for three winding transformer '$originalTransformer'."
          )
      }
    }
  }

  /** Method for updating [[Transformer2WInput]]s.
    *
    * @param transformer2Ws
    *   sequence of transformers
    * @param nodeMapping
    *   a map: uuids to updated nodes
    * @param typeInputs
    *   sequence of available [[Transformer2WTypeInput]]s
    * @return
    *   a sequence of updated [[Transformer2WInput]]s wrapped by a [[Success]]
    *   if all transformers are updated correctly
    */
  private def updateTransformer2Ws(
      transformer2Ws: Seq[Transformer2WInput],
      nodeMapping: Map[UUID, NodeInput],
      typeInputs: Seq[Transformer2WTypeInput]
  ): Try[Seq[Transformer2WInput]] = Try {
    transformer2Ws.map { originalTransformer =>
      val originalA = originalTransformer.getNodeA.getUuid
      val originalB = originalTransformer.getNodeB.getUuid
      val originalType = originalTransformer.getType
      val copy = originalTransformer.copy()

      // update nodes
      var updated =
        (nodeMapping.get(originalA), nodeMapping.get(originalB)) match {
          case (Some(nodeA), Some(nodeB)) => copy.nodeA(nodeA).nodeB(nodeB)
          case (Some(nodeA), None)        => copy.nodeA(nodeA)
          case (None, Some(nodeB))        => copy.nodeB(nodeB)
        }

      // check if voltage ratings have changed
      val voltLvlA = nodeMapping(originalA).getVoltLvl.getNominalVoltage
      val voltLvlB = nodeMapping(originalB).getVoltLvl.getNominalVoltage

      if (
        originalType.getvRatedA() != voltLvlA || originalType
          .getvRatedB() != voltLvlB
      ) {
        typeInputs.find { t =>
          t.getvRatedA() == voltLvlA && t.getvRatedB() == voltLvlB
        } match {
          case Some(value) => updated = updated.`type`(value)
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
    * @param transformer3Ws
    *   sequence of transformers
    * @param nodeMapping
    *   a map: uuids to updated nodes
    * @param typeInputs
    *   sequence of available [[Transformer3WTypeInput]]s
    * @return
    *   a sequence of updated [[Transformer3WInput]]s wrapped by a [[Success]]
    *   if all transformers are updated correctly
    */
  private def updateTransformer3Ws(
      transformer3Ws: Seq[Transformer3WInput],
      nodeMapping: Map[UUID, NodeInput],
      typeInputs: Seq[Transformer3WTypeInput]
  ): Try[Seq[Transformer3WInput]] = Try {
    transformer3Ws.map { originalTransformer =>
      val originalA = originalTransformer.getNodeA.getUuid
      val originalB = originalTransformer.getNodeB.getUuid
      val originalC = originalTransformer.getNodeC.getUuid
      val originalType = originalTransformer.getType
      val copy = originalTransformer.copy()

      // update nodes
      var updated = nodeMapping
        .get(originalA)
        .map { nodeA => copy.nodeA(nodeA) }
        .getOrElse(copy)
      updated = nodeMapping
        .get(originalB)
        .map { nodeB => copy.nodeB(nodeB) }
        .getOrElse(copy)
      updated = nodeMapping
        .get(originalC)
        .map { nodeC => copy.nodeC(nodeC) }
        .getOrElse(copy)

      // check if voltage ratings have changed
      val voltLvlA = nodeMapping(originalA).getVoltLvl.getNominalVoltage
      val voltLvlB = nodeMapping(originalB).getVoltLvl.getNominalVoltage
      val voltLvlC = nodeMapping(originalC).getVoltLvl.getNominalVoltage

      if (
        originalType.getvRatedA() != voltLvlA || originalType
          .getvRatedB() != voltLvlB || originalType.getvRatedC() != voltLvlC
      ) {
        typeInputs.find { t =>
          t.getvRatedA() == voltLvlA && t.getvRatedB() == voltLvlB && t
            .getvRatedC() == voltLvlC
        } match {
          case Some(value) => updated = updated.`type`(value)
          case None =>
            throw GridException(
              s"No transformer3WType for voltage levels $voltLvlA, $voltLvlB and $voltLvlC found!"
            )
        }
      }

      updated.build()
    }
  }

  /** Update the node references in [[MeasurementUnitInput]]s
    *
    * @param measurements
    *   Collection of [[MeasurementUnitInput]] to update
    * @param nodeMapping
    *   Mapping from node [[UUID]] to updated nodes
    * @return
    *   Measurement devices with updated node references
    */
  private def updateMeasurements(
      measurements: Seq[MeasurementUnitInput],
      nodeMapping: Map[UUID, NodeInput]
  ): Try[Seq[MeasurementUnitInput]] = Try {
    measurements.map { originalMeasurement =>
      nodeMapping.get(originalMeasurement.getNode.getUuid) match {
        case Some(node) =>
          originalMeasurement.copy().node(node).build()
        case None =>
          throw GridException(
            s"Unable to determine new node for measurement unit '$originalMeasurement'."
          )
      }
    }
  }

  /** Update the entirety of system participants
    *
    * @param participants
    *   The system participant container
    * @param nodeMapping
    *   Mapping from node [[UUID]] to updated node
    * @return
    *   A container of updated system participants
    */
  private def updateSystemParticipants(
      participants: SystemParticipants,
      nodeMapping: Map[UUID, NodeInput]
  ): Try[SystemParticipants] = Try {
    val bms = updateParticipants(
      participants.getBmPlants.asScala.toSeq,
      nodeMapping,
      (bm: BmInput, node: NodeInput) => bm.copy().node(node).build()
    ).fold(
      exception =>
        throw GridException(
          "Unable to update node references of biomass plants.",
          exception
        ),
      identity
    )

    val chps = updateParticipants(
      participants.getChpPlants.asScala.toSeq,
      nodeMapping,
      (chp: ChpInput, node: NodeInput) => chp.copy().node(node).build()
    ).fold(
      exception =>
        throw GridException(
          "Unable to update node references of combined heat and power plants.",
          exception
        ),
      identity
    )

    val evcss = updateParticipants(
      participants.getEvcs.asScala.toSeq,
      nodeMapping,
      (evcs: EvcsInput, node: NodeInput) => evcs.copy().node(node).build()
    ).fold(
      exception =>
        throw GridException(
          "Unable to update node references of charging stations.",
          exception
        ),
      identity
    )

    val evs = updateParticipants(
      participants.getEvs.asScala.toSeq,
      nodeMapping,
      (ev: EvInput, node: NodeInput) => ev.copy().node(node).build()
    ).fold(
      exception =>
        throw GridException(
          "Unable to update node references of electric vehicles.",
          exception
        ),
      identity
    )

    val ffis = updateParticipants(
      participants.getFixedFeedIns.asScala.toSeq,
      nodeMapping,
      (ffi: FixedFeedInInput, node: NodeInput) => ffi.copy().node(node).build()
    ).fold(
      exception =>
        throw GridException(
          "Unable to update node references of fixed feed ins.",
          exception
        ),
      identity
    )

    val hps = updateParticipants(
      participants.getHeatPumps.asScala.toSeq,
      nodeMapping,
      (hp: HpInput, node: NodeInput) => hp.copy().node(node).build()
    ).fold(
      exception =>
        throw GridException(
          "Unable to update node references of heat pumps.",
          exception
        ),
      identity
    )

    val loads = updateParticipants(
      participants.getLoads.asScala.toSeq,
      nodeMapping,
      (load: LoadInput, node: NodeInput) => load.copy().node(node).build()
    ).fold(
      exception =>
        throw GridException(
          "Unable to update node references of loads.",
          exception
        ),
      identity
    )

    val pvs = updateParticipants(
      participants.getPvPlants.asScala.toSeq,
      nodeMapping,
      (pv: PvInput, node: NodeInput) => pv.copy().node(node).build()
    ).fold(
      exception =>
        throw GridException(
          "Unable to update node references of participants.",
          exception
        ),
      identity
    )

    val storages = updateParticipants(
      participants.getStorages.asScala.toSeq,
      nodeMapping,
      (storage: StorageInput, node: NodeInput) =>
        storage.copy().node(node).build()
    ).fold(
      exception =>
        throw GridException(
          "Unable to update node references of storages.",
          exception
        ),
      identity
    )

    val wecs = updateParticipants(
      participants.getWecPlants.asScala.toSeq,
      nodeMapping,
      (wec: WecInput, node: NodeInput) => wec.copy().node(node).build()
    ).fold(
      exception =>
        throw GridException(
          "Unable to update node references of wind energy converters.",
          exception
        ),
      identity
    )

    new SystemParticipants(
      bms.toSet.asJava,
      chps.toSet.asJava,
      evcss.toSet.asJava,
      evs.toSet.asJava,
      ffis.toSet.asJava,
      hps.toSet.asJava,
      loads.toSet.asJava,
      pvs.toSet.asJava,
      storages.toSet.asJava,
      wecs.toSet.asJava
    )
  }

  /** Update the given participants
    *
    * @param participants
    *   Participants to update
    * @param nodeMapping
    *   Mapping from node [[UUID]] to updated nodes
    * @param updateFunc
    *   Function, that does the update for the participant
    * @tparam T
    *   Type of the participant
    * @return
    *   A sequence of updated participants
    */
  private def updateParticipants[T <: SystemParticipantInput](
      participants: Seq[T],
      nodeMapping: Map[UUID, NodeInput],
      updateFunc: (T, NodeInput) => T
  ): Try[Seq[T]] = Try {
    participants.map { originalParticipant =>
      nodeMapping.get(originalParticipant.getNode.getUuid) match {
        case Some(node) =>
          updateFunc(originalParticipant, node)
        case None =>
          throw GridException(
            s"Unable to determine new node for system participant '$originalParticipant'."
          )
      }
    }
  }

  private def updateGraphics(
      graphics: GraphicElements,
      nodeMapping: Map[UUID, NodeInput],
      lineMapping: Map[UUID, LineInput]
  ) = Try {
    val nodes = graphics.getNodeGraphics.asScala.map { nodeGraphic =>
      nodeMapping.get(nodeGraphic.getNode.getUuid) match {
        case Some(node) =>
          new NodeGraphicInput(
            nodeGraphic.getUuid,
            nodeGraphic.getGraphicLayer,
            nodeGraphic.getPath,
            node,
            nodeGraphic.getPoint
          )
        case None =>
          throw GridException(
            s"Unable to determine new node for node graphic '$nodeGraphic'."
          )
      }
    }
    val lines = graphics.getLineGraphics.asScala.map { lineGraphic =>
      lineMapping.get(lineGraphic.getLine.getUuid) match {
        case Some(line) =>
          new LineGraphicInput(
            lineGraphic.getUuid,
            lineGraphic.getGraphicLayer,
            lineGraphic.getPath,
            line
          )
        case None =>
          throw GridException(
            s"Unable to determine new node for line graphic '$lineGraphic'."
          )
      }
    }

    new GraphicElements(nodes.toSet.asJava, lines.toSet.asJava)
  }
}
