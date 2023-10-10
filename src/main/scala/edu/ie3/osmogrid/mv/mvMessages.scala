/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.mv

import akka.actor.typed.ActorRef
import edu.ie3.datamodel.models.input.NodeInput
import edu.ie3.datamodel.models.input.connector.LineInput
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.exception.RequestFailedException
import edu.ie3.osmogrid.graph.OsmGraph
import utils.GridConversion.NodeConversion
import edu.ie3.osmogrid.io.input.{InputDataEvent, InputDataProvider, Response}
import edu.ie3.osmogrid.model.OsmoGridModel.MvOsmoGridModel
import edu.ie3.osmogrid.mv.MvGraphBuilder.MvGraph
import org.slf4j.Logger
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
  * @param nodes
  *   of the subgrid
  * @param lines
  *   of the subgrid
  */
final case class FinishedMvGridData(
    nodes: Set[NodeInput],
    lines: Set[LineInput]
) extends MvResponse

object ReqMvGrids extends MvRequest

final case class StartGeneration(
    lvGrids: List[SubGridContainer],
    hvGrids: Option[List[SubGridContainer]],
    streetGraph: OsmGraph
) extends MvRequest

final case class ProvideLvData(
    lvGrids: Seq[SubGridContainer],
    streetGraph: OsmGraph
) extends MvResponse

final case class ProvideHvData(
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
    case ProvideLvData(grids, graph) =>
      log.debug(s"Received lv data.")
      Success(copy(lvGrids = Some(grids.toList), streetGraph = Some(graph)))

    case ProvideHvData(grids) =>
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
