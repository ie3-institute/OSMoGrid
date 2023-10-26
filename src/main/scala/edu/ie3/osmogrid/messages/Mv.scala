/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.messages

import akka.actor.typed.ActorRef
import edu.ie3.datamodel.models.input.NodeInput
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.exception.{IllegalStateException, RequestFailedException}
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.osmogrid.io.input
import edu.ie3.osmogrid.io.input.{AssetInformation, RepAssetTypes}
import edu.ie3.osmogrid.messages.Mv.MvMessageAdapters.WrappedInputResponse
import org.slf4j.Logger
import utils.GridConversion.NodeConversion
import utils.VoronoiUtils.VoronoiPolygon

import scala.util.{Failure, Success, Try}

object Mv {

  /** Trait for mv requests.
    */
  sealed trait MvRequest

  /** Trait for mv responses
    */
  sealed trait MvResponse

  /** Wraps a [[MvResponse]].
    *
    * @param response
    *   to be wrapped
    */
  final case class WrappedMvResponse(
      response: MvResponse
  ) extends MvRequest

  /** Mv termination request.
    */
  object MvTerminate extends MvRequest

  /** Request for mv grids.
    */
  object ReqMvGrids extends MvRequest

  /** Request for mv graph generation.
    *
    * @param nr
    *   of the graph
    * @param polygon
    *   with components for the graph
    * @param streetGraph
    *   with all street nodes
    */
  final case class StartGraphGeneration(
      nr: Int,
      polygon: VoronoiPolygon,
      streetGraph: OsmGraph,
      assetInformation: AssetInformation
  ) extends MvRequest

  /** Request for mv graph conversion.
    *
    * @param nr
    *   of the graph
    * @param graph
    *   with grid structure
    * @param nodeConversion
    *   for converting osm nodes into corresponding PSDM nodes
    */
  final case class StartGraphConversion(
      nr: Int,
      graph: OsmGraph,
      nodeConversion: NodeConversion,
      assetInformation: AssetInformation
  ) extends MvRequest

  /** Response for a mv coordinator that contains the converted grid structure
    * as nodes and lines.
    *
    * @param subGridContainer
    *   container with the generated grid
    * @param nodeChanges
    *   nodes that were changed during mv generation
    */
  final case class FinishedMvGridData(
      subGridContainer: SubGridContainer,
      nodeChanges: Seq[NodeInput]
  ) extends MvResponse

  final case class StartGeneration(
      lvGrids: Seq[SubGridContainer],
      hvGrids: Option[Seq[SubGridContainer]],
      streetGraph: OsmGraph,
      assetInformation: AssetInformation
  ) extends MvRequest

  /** Replying the generated medium voltage grids
    *
    * @param grids
    *   Collection of medium voltage grids
    * @param nodeChanges
    *   updated nodes
    */
  final case class RepMvGrids(
      grids: Seq[SubGridContainer],
      nodeChanges: Seq[NodeInput]
  ) extends MvResponse

  final case class ProvidedLvData(
      lvGrids: Seq[SubGridContainer],
      streetGraph: OsmGraph
  ) extends MvResponse

  final case class ProvidedHvData(
      hvGrids: Seq[SubGridContainer]
  ) extends MvResponse

  final case class MvMessageAdapters(
      inputDataProvider: ActorRef[input.Response]
  )

  object MvMessageAdapters {
    final case class WrappedInputResponse(
        response: input.Response
    ) extends MvRequest
  }

  final case class AwaitingInputData(
      cfg: OsmoGridConfig.Generation.Mv,
      runGuardian: ActorRef[MvResponse],
      lvGrids: Option[Seq[SubGridContainer]],
      hvGrids: Option[Seq[SubGridContainer]],
      streetGraph: Option[OsmGraph],
      assetInformation: Option[AssetInformation]
  ) {
    def registerResponse(
        response: MvRequest,
        log: Logger
    ): Try[AwaitingInputData] = response match {
      case WrappedInputResponse(RepAssetTypes(assetInformation)) =>
        log.debug(s"Received asset type data.")
        Success(copy(assetInformation = Some(assetInformation)))

      case WrappedMvResponse(ProvidedLvData(grids, graph)) =>
        log.debug(s"Received lv data.")
        Success(copy(lvGrids = Some(grids), streetGraph = Some(graph)))

      case WrappedMvResponse(ProvidedHvData(grids)) =>
        log.debug(s"Received hv data.")
        Success(copy(hvGrids = Some(grids)))
      case other =>
        Failure(RequestFailedException(s"$other is not supported!"))
    }

    def isComprehensive: Boolean = {
      val needed =
        lvGrids.isDefined && streetGraph.isDefined && assetInformation.isDefined

      if (!cfg.spawnMissingHvNodes) {
        needed
      } else {
        needed && hvGrids.isDefined
      }
    }
  }

  object AwaitingInputData {
    def empty(
        cfg: OsmoGridConfig.Generation.Mv,
        runGuardian: ActorRef[MvResponse]
    ): AwaitingInputData =
      AwaitingInputData(cfg, runGuardian, None, None, None, None)
  }

  /** Class for medium voltage result data.
    *
    * @param subnets
    *   a set of sub grids that are not generated yet
    * @param subGridContainer
    *   all finished sub grids
    * @param nodes
    *   that were changed during the mv generation
    */
  final case class MvResultData(
      subnets: Set[Int],
      subGridContainer: Seq[SubGridContainer],
      nodes: Seq[NodeInput]
  ) extends MvRequest {

    /** Method for updating the [[MvResultData]].
      *
      * @param subgrid
      *   that was generated
      * @param nodeChanges
      *   [[NodeInput]]s that were changed
      * @return
      *   an updated [[MvResultData]]
      */
    def update(
        subgrid: SubGridContainer,
        nodeChanges: Seq[NodeInput]
    ): MvResultData = {
      if (subnets.contains(subgrid.getSubnet)) {
        MvResultData(
          subnets - subgrid.getSubnet,
          subGridContainer :+ subgrid,
          nodes ++ nodeChanges
        )
      } else {
        throw IllegalStateException(
          s"Trying to update with subgrid container that was not expected. Subnet: ${subgrid.getSubnet}"
        )
      }
    }
  }

  object MvResultData {
    def empty(subnets: Set[Int]): MvResultData = {
      MvResultData(subnets, Seq.empty, Seq.empty)
    }
  }
}
