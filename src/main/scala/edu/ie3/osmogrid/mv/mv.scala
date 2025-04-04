/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.mv

import edu.ie3.datamodel.models.input.NodeInput
import edu.ie3.datamodel.models.input.container.{
  JointGridContainer,
  SubGridContainer,
}
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.exception.{
  IllegalConfigException,
  IllegalStateException,
  RequestFailedException,
}
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.osmogrid.io.input.{
  AssetInformation,
  InputResponse,
  RepAssetTypes,
}
import edu.ie3.osmogrid.mv.MvMessageAdapters.WrappedInputResponse
import org.apache.pekko.actor.typed.ActorRef
import org.slf4j.Logger
import utils.GridConversion.NodeConversion
import VoronoiPolygonSupport.VoronoiPolygon

import java.util.UUID
import scala.util.{Failure, Success, Try}

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
  * @param mvSlackNode
  *   option for an uuid of a mv slack node
  * @param streetGraph
  *   with all street nodes
  */
final case class StartMvGraphGeneration(
    nr: Int,
    polygon: VoronoiPolygon,
    mvSlackNode: Option[UUID],
    streetGraph: OsmGraph,
    assetInformation: AssetInformation,
) extends MvRequest

/** Request for mv graph conversion.
  *
  * @param nr
  *   of the graph
  * @param graph
  *   with grid structure
  * @param hvNode
  *   option for an uuid a mv slack node
  * @param nodeConversion
  *   for converting osm nodes into corresponding PSDM nodes
  */
final case class StartMvGraphConversion(
    nr: Int,
    graph: OsmGraph,
    hvNode: Option[UUID],
    nodeConversion: NodeConversion,
    assetInformation: AssetInformation,
) extends MvRequest

/** Response for a mv coordinator that contains the converted grid structure as
  * nodes and lines.
  *
  * @param subGridContainer
  *   container with the generated grid
  * @param nodeChanges
  *   nodes that were changed during mv generation
  */
private[mv] final case class FinishedMvGridData(
    subGridContainer: SubGridContainer,
    nodeChanges: Map[UUID, NodeInput],
) extends MvResponse

/** Request for a mv coordinator to start the generation of medium voltage
  * grids.
  *
  * @param lvGrids
  *   low voltage grids
  * @param hvGrids
  *   option for high voltage grids
  * @param streetGraph
  *   a graph of the streets
  * @param assetInformation
  *   information for assets
  */
final case class StartMvGeneration(
    lvGrids: Seq[SubGridContainer],
    hvGrids: Option[Seq[SubGridContainer]],
    streetGraph: OsmGraph,
    assetInformation: AssetInformation,
) extends MvRequest

/** Replying the generated medium voltage grids
  *
  * @param grids
  *   Collection of medium voltage grids
  * @param dummyHvGrid
  *   if one was spawned
  * @param nodeChanges
  *   updated nodes
  * @param assetInformation
  *   contains information for assets
  */
final case class RepMvGrids(
    grids: Seq[SubGridContainer],
    dummyHvGrid: Option[JointGridContainer],
    nodeChanges: Map[UUID, NodeInput],
    assetInformation: AssetInformation,
) extends MvResponse

/** Message for providing some low voltage data to the mv coordinator.
  *
  * @param lvGrids
  *   low voltage grids
  * @param streetGraph
  *   a graph of the streets
  */
final case class ProvidedLvData(
    lvGrids: Seq[SubGridContainer],
    streetGraph: OsmGraph,
) extends MvResponse

/** Message for providing some high voltage data to the mv coordinator.
  *
  * @param hvGrids
  *   high voltage grids
  */
final case class ProvidedHvData(
    hvGrids: Seq[SubGridContainer]
) extends MvResponse

/** Utility class that contains some message adapters.
  *
  * @param inputDataProvider
  *   adapter for input data provider
  */
private[mv] final case class MvMessageAdapters(
    inputDataProvider: ActorRef[InputResponse]
)

private[mv] object MvMessageAdapters {
  final case class WrappedInputResponse(
      response: InputResponse
  ) extends MvRequest
}

/** State data for awaiting data state.
  *
  * @param cfg
  *   config for mv generation
  * @param hvGridsRequired
  *   true if we either need a dummy hv node or actual hv grids
  * @param runGuardian
  *   superior actor
  * @param lvGrids
  *   option for lv grids
  * @param hvGrids
  *   option for hv grids
  * @param streetGraph
  *   a graph of the streets
  * @param assetInformation
  *   information for assets
  */
private[mv] final case class AwaitingInputData(
    cfg: OsmoGridConfig.Generation.Mv,
    hvGridsRequired: Boolean,
    runGuardian: ActorRef[MvResponse],
    lvGrids: Option[Seq[SubGridContainer]],
    hvGrids: Option[Seq[SubGridContainer]],
    streetGraph: Option[OsmGraph],
    assetInformation: Option[AssetInformation],
) {

  /** Method for registering responses.
    *
    * @param response
    *   with data
    * @param log
    *   to log information
    * @return
    *   a try
    */
  def registerResponse(
      response: MvRequest,
      log: Logger,
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

  /** Returns true if all awaited data were received.
    */
  def isComplete: Boolean = {
    val needed =
      lvGrids.isDefined && streetGraph.isDefined && assetInformation.isDefined

    if (hvGridsRequired) {
      if (cfg.spawnMissingHvNodes) {
        needed
      } else {
        needed && hvGrids.isDefined
      }
    } else {
      needed
    }
  }
}

private[mv] object AwaitingInputData {

  /** Method for creating an empty [[AwaitingInputData]].
    *
    * @param cfg
    *   config for mv generation
    * @param runGuardian
    *   superior actor
    * @return
    *   an empty [[AwaitingInputData]]
    */
  def empty(
      cfg: OsmoGridConfig,
      runGuardian: ActorRef[MvResponse],
  ): AwaitingInputData =
    AwaitingInputData(
      cfg.generation.mv.getOrElse(
        throw IllegalConfigException(s"No medium voltage config found in $cfg!")
      ),
      cfg.grids.output.hv,
      runGuardian,
      None,
      None,
      None,
      None,
    )
}

/** Class for medium voltage result data.
  *
  * @param subnets
  *   a set of sub grids that are not generated yet
  * @param dummyHvGrid
  *   if one was spawned
  * @param subGridContainer
  *   all finished sub grids
  * @param nodes
  *   that were changed during the mv generation
  * @param assetInformation
  *   information for assets
  */
private[mv] final case class MvResultData(
    subnets: Set[Int],
    dummyHvGrid: Option[JointGridContainer],
    subGridContainer: Seq[SubGridContainer],
    nodes: Map[UUID, NodeInput],
    assetInformation: AssetInformation,
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
      nodeChanges: Map[UUID, NodeInput],
  ): MvResultData = {
    if (subnets.contains(subgrid.getSubnet)) {
      MvResultData(
        subnets - subgrid.getSubnet,
        dummyHvGrid,
        subGridContainer :+ subgrid,
        nodes ++ nodeChanges,
        assetInformation,
      )
    } else {
      throw IllegalStateException(
        s"Trying to update with subgrid container that was not expected. Subnet: ${subgrid.getSubnet}"
      )
    }
  }
}

private[mv] object MvResultData {

  /** Method for creating an empty [[MvResultData]].
    *
    * @param subnets
    *   sequence of sub grid numbers
    * @param dummyHvGrid
    *   if one was spawned
    * @param assetInformation
    *   information for assets
    * @return
    *   an empty [[MvResultData]]
    */
  def empty(
      subnets: Set[Int],
      dummyHvGrid: Option[JointGridContainer],
      assetInformation: AssetInformation,
  ): MvResultData = {
    MvResultData(subnets, dummyHvGrid, Seq.empty, Map.empty, assetInformation)
  }
}
