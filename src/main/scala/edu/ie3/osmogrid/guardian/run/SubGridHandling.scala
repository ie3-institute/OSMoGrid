/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.guardian.run

import akka.actor.typed.ActorRef
import edu.ie3.datamodel.models.input.connector._
import edu.ie3.datamodel.models.input.container.{
  GraphicElements,
  RawGridElements,
  SubGridContainer,
  SystemParticipants
}
import edu.ie3.datamodel.models.input.graphics.{
  LineGraphicInput,
  NodeGraphicInput
}
import edu.ie3.datamodel.models.input.system._
import edu.ie3.datamodel.models.input.{MeasurementUnitInput, NodeInput}
import edu.ie3.datamodel.utils.ContainerUtils
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.exception.GridException
import edu.ie3.osmogrid.guardian.run.SubGridHandling.assignSubnetNumbers
import edu.ie3.osmogrid.io.output.ResultListenerProtocol
import org.slf4j.Logger

import java.util.UUID
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

trait SubGridHandling {

  /** Handle incoming low voltage grid results
    *
    * @param grids
    *   Received grids
    * @param cfg
    *   Grid generation config
    */
  protected def handleLvResults(
      grids: Seq[SubGridContainer],
      cfg: OsmoGridConfig.Generation,
      resultListener: Seq[ActorRef[ResultListenerProtocol]],
      msgAdapters: MessageAdapters
  )(implicit log: Logger): Try[Unit] = {
    log.info("All lv grids successfully generated.")
    assignSubnetNumbers(grids).map { updatedSubGrids =>
      log.debug(
        "No further generation steps intended. Hand over results to result handler."
      )
      /* Bundle grid result and inform interested listeners */
      val jointGrid =
        ContainerUtils.combineToJointGrid(updatedSubGrids.asJava)
      resultListener.foreach { listener =>
        listener ! ResultListenerProtocol.GridResult(jointGrid)
      }
    }
  }
}

object SubGridHandling {
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
      participants.getEvCS.asScala.toSeq,
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

    val ems = updateParticipants(
      participants.getEmSystems.asScala.toSeq,
      nodeMapping,
      (em: EmInput, node: NodeInput) => em.copy().node(node).build()
    ).fold(
      exception =>
        throw GridException(
          "Unable to update node references of em systems.",
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
      wecs.toSet.asJava,
      ems.toSet.asJava
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
