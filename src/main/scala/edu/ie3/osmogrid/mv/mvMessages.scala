/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.mv

import akka.actor.typed.ActorRef
import edu.ie3.datamodel.models.input.NodeInput
import edu.ie3.datamodel.models.input.connector.TransformerInput
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.exception.{
  IllegalStateException,
  RequestFailedException
}
import edu.ie3.osmogrid.graph.OsmGraph
import org.slf4j.Logger
import utils.GridConversion.NodeConversion
import utils.VoronoiUtils.VoronoiPolygon

import scala.util.{Failure, Success, Try}

/** Trait for mv requests.
  */
sealed trait MvRequest

/** Trait for mv responses
  */
sealed trait MvResponse

/** Wraps a [[MvResponse]].
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
  * @param nr
  *   of the graph
  * @param polygon
  *   with components for the graph
  * @param streetGraph
  *   with all street nodes
  * @param cfg
  *   for mv generation
  */
final case class StartGraphGeneration(
    nr: Int,
    polygon: VoronoiPolygon,
    streetGraph: OsmGraph,
    cfg: OsmoGridConfig.Generation.Mv
) extends MvRequest

/** Request for mv graph conversion.
  * @param nr
  *   of the graph
  * @param graph
  *   with grid structure
  * @param nodeConversion
  *   for converting osm nodes into corresponding PSDM nodes
  * @param cfg
  *   for ,v generation
  */
final case class StartGraphConversion(
    nr: Int,
    graph: OsmGraph,
    nodeConversion: NodeConversion,
    cfg: OsmoGridConfig.Generation.Mv
) extends MvRequest

/** Response for a mv coordinator that contains the converted grid structure as
  * nodes and lines.
  * @param subGridContainer
  *   container with the generated grid
  * @param nodeChanges
  *   nodes that were changed during mv generation
  * @param transformerChanges
  *   nodes that were changed during mv generation
  */
final case class FinishedMvGridData(
    subGridContainer: SubGridContainer,
    nodeChanges: Seq[NodeInput],
    transformerChanges: Seq[TransformerInput]
) extends MvResponse

final case class StartGeneration(
    lvGrids: List[SubGridContainer],
    hvGrids: Option[List[SubGridContainer]],
    streetGraph: OsmGraph
) extends MvRequest

/** Replying the generated medium voltage grids
  *
  * @param grids
  *   Collection of medium voltage grids
  * @param nodeChanges
  *   updated nodes
  * @param transformerChanges
  *   updated transformers
  */
final case class RepMvGrids(
    grids: Seq[SubGridContainer],
    nodeChanges: Seq[NodeInput],
    transformerChanges: Seq[TransformerInput]
) extends MvResponse

final case class ProvidedLvData(
    lvGrids: Seq[SubGridContainer],
    streetGraph: OsmGraph
) extends MvResponse

final case class ProvidedHvData(
    hvGrids: Seq[SubGridContainer]
) extends MvResponse

final case class AwaitingInputData(
    cfg: OsmoGridConfig.Generation.Mv,
    runGuardian: ActorRef[MvResponse],
    lvGrids: Option[List[SubGridContainer]],
    hvGrids: Option[List[SubGridContainer]],
    streetGraph: Option[OsmGraph]
) {
  def registerResponse(
      mvResponse: MvResponse,
      log: Logger
  ): Try[AwaitingInputData] = mvResponse match {
    case ProvidedLvData(grids, graph) =>
      log.debug(s"Received lv data.")
      Success(copy(lvGrids = Some(grids.toList), streetGraph = Some(graph)))

    case ProvidedHvData(grids) =>
      log.debug(s"Received hv data.")
      Success(copy(hvGrids = Some(grids.toList)))

    case other =>
      Failure(RequestFailedException(s"$other is not supported!"))
  }

  def isComprehensive: Boolean = {
    if (cfg.spawnMissingHvNodes) {
      lvGrids.isDefined && streetGraph.isDefined
    } else {
      lvGrids.isDefined && streetGraph.isDefined && hvGrids.isDefined
    }
  }
}

object AwaitingInputData {
  def empty(
      cfg: OsmoGridConfig.Generation.Mv,
      runGuardian: ActorRef[MvResponse]
  ): AwaitingInputData =
    AwaitingInputData(cfg, runGuardian, None, None, None)
}

/** Class for medium voltage result data.
  * @param subnets
  *   a set of subnets that are not generated yet
  * @param nodes
  *   that were changed during the mv generation
  * @param transformers
  *   that were changed during the mv generation
  */
final case class MvResultData(
    subnets: Set[Int],
    nodes: Seq[NodeInput],
    transformers: Seq[TransformerInput]
) extends MvRequest {

  /** Method for updating the [[MvResultData]].
    * @param subnet
    *   that finished generation
    * @param nodeChanges
    *   [[NodeInput]]s that were changed
    * @param transformerChanges
    *   [[TransformerInput]]s that were changed
    * @return
    *   an updated [[MvResultData]]
    */
  def update(
      subnet: Int,
      nodeChanges: Seq[NodeInput],
      transformerChanges: Seq[TransformerInput]
  ): MvResultData = {
    if (subnets.contains(subnet)) {
      MvResultData(
        subnets - subnet,
        nodes ++ nodeChanges,
        transformers ++ transformerChanges
      )
    } else {
      throw IllegalStateException(
        s"Trying to update with subgrid container that was not expected. Subnet: $subnet"
      )
    }
  }
}

object MvResultData {
  def empty(subnets: Set[Int]): MvResultData = {
    MvResultData(subnets, Seq.empty, Seq.empty)
  }
}
