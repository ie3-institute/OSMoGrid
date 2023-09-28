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
import edu.ie3.osmogrid.grid.GridConversion.NodeConversion
import edu.ie3.osmogrid.io.input.{InputDataEvent, InputDataProvider, Response}
import edu.ie3.osmogrid.model.OsmoGridModel.MvOsmoGridModel
import edu.ie3.osmogrid.mv.MvGraphBuilder.MvGraph
import org.slf4j.Logger
import utils.VoronoiUtils.VoronoiPolygon

import scala.util.{Failure, Success, Try}

sealed trait MvRequest
sealed trait MvResponse

object ReqMvGrids extends MvRequest
object MvTerminate extends MvRequest

final case class StartGraphGeneration(
    nr: Int,
    polygon: VoronoiPolygon,
    streetGraph: OsmGraph,
    cfg: OsmoGridConfig.Generation.Mv
) extends MvRequest
final case class StartGraphConversion(
    nr: Int,
    graph: OsmGraph,
    nodeConversion: NodeConversion,
    cfg: OsmoGridConfig.Generation.Mv
) extends MvRequest

final case class IdleData(
    cfg: OsmoGridConfig.Generation.Mv,
    inputDataProvider: ActorRef[InputDataEvent],
    runGuardian: ActorRef[MvResponse],
    msgAdapter: MvMessageAdapters
)

final case class StartMvGeneration(
    cfg: OsmoGridConfig.Generation.Mv,
    lvGrids: List[SubGridContainer],
    hvGrids: List[SubGridContainer],
    streetGraph: OsmGraph
) extends MvRequest

final case class StartMvGraphConversion(
    cfg: OsmoGridConfig.Generation.Mv,
    graphs: List[MvGraph]
) extends MvRequest

final case class FinishedMvGridData(
    nodes: Set[NodeInput],
    lines: Set[LineInput]
) extends MvRequest

final case class MvMessageAdapters(
    inputDataProvider: ActorRef[Response]
)

object MvMessageAdapters {
  final case class WrappedInputDataResponse(
      response: Response
  ) extends MvRequest
}

private final case class AwaitingMvInputData(
    osmData: Option[MvOsmoGridModel],
    cfg: OsmoGridConfig.Generation.Mv,
    msgAdapters: MvMessageAdapters,
    guardian: ActorRef[MvResponse]
) {
  def registerResponse(
      response: Response,
      log: Logger
  ): Try[AwaitingMvInputData] = ???
  /*response match {
    case InputDataProvider.RepOsm(osmModel: MvOsmoGridModel) =>
      log.debug(s"Received MV osm model.")
      Success(copy(osmData = Some(osmModel)))
    /* Those states correspond to failed operation */
    case InputDataProvider.OsmReadFailed(reason) =>
      Failure(
        RequestFailedException(
          "The requested OSM data cannot be read. Stop generation",
          reason
        )
      )
  }
   */

  def isComplete: Boolean = osmData.isDefined
}

private object AwaitingMvInputData {
  def empty(mvCoordinatorData: IdleData): AwaitingMvInputData =
    AwaitingMvInputData(
      None,
      mvCoordinatorData.cfg,
      mvCoordinatorData.msgAdapter,
      mvCoordinatorData.runGuardian
    )
}

private final case class AwaitingMvGraphData(
    numberOfGraphs: Int,
    graphs: List[MvGraph],
    cfg: OsmoGridConfig.Generation.Mv,
    msgAdapters: MvMessageAdapters,
    guardian: ActorRef[MvResponse]
) {
  def completed(): Int = graphs.size

  def uncompleted(): Int = numberOfGraphs - completed

  def isComplete: Boolean = graphs.size == numberOfGraphs
}

private object AwaitingMvGraphData {
  def empty(
      numberOfGraphs: Int,
      awaitingMvInputData: AwaitingMvInputData
  ): AwaitingMvGraphData =
    AwaitingMvGraphData(
      numberOfGraphs,
      List.empty,
      awaitingMvInputData.cfg,
      awaitingMvInputData.msgAdapters,
      awaitingMvInputData.guardian
    )
}
