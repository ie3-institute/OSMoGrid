/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.mv

import akka.actor.typed.ActorRef
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.exception.RequestFailedException
import edu.ie3.osmogrid.io.input.InputDataProvider
import edu.ie3.osmogrid.model.OsmoGridModel.MvOsmoGridModel
import org.slf4j.Logger

import scala.util.{Success, Try, Failure}

sealed trait MvRequest
sealed trait MvResponse

object ReqMvGrids extends MvRequest

object MvTerminate extends MvRequest

final case class IdleData(
    cfg: OsmoGridConfig.Generation.Mv,
    inputDataProvider: ActorRef[InputDataProvider.InputDataEvent],
    runGuardian: ActorRef[MvResponse],
    msgAdapter: MvMessageAdapters
)

final case class StartMvGeneration(
    cfg: OsmoGridConfig.Generation.Mv,
    lvGrids: List[SubGridContainer],
    hvGrids: List[SubGridContainer],
    osmGridModel: MvOsmoGridModel
) extends MvRequest

final case class StartMvGraphConversion(
    cfg: OsmoGridConfig.Generation.Mv,
    graphs: List[MvGraph]
) extends MvRequest

final case class FinishedMvGraph(
    mvGraph: MvGraph
) extends MvRequest

final case class MvMessageAdapters(
    inputDataProvider: ActorRef[InputDataProvider.Response]
)

object MvMessageAdapters {
  final case class WrappedInputDataResponse(
      response: InputDataProvider.Response
  ) extends MvRequest
}

private final case class AwaitingMvInputData(
    osmData: Option[MvOsmoGridModel],
    lvGridData: Option[List[SubGridContainer]],
    hvGridData: Option[List[SubGridContainer]],
    cfg: OsmoGridConfig.Generation.Mv,
    msgAdapters: MvMessageAdapters,
    guardian: ActorRef[MvResponse]
) {
  def registerResponse(
      response: InputDataProvider.Response,
      log: Logger
  ): Try[AwaitingMvInputData] = response match {
    case InputDataProvider.RepOsm(osmModel: MvOsmoGridModel) =>
      log.debug(s"Received MV osm model.")
      Success(copy(osmData = Some(osmModel)))
    case InputDataProvider.RepLv(lvData) =>
      log.debug("Received LV grid data.")
      Success(copy(lvGridData = Some(lvData)))
    case InputDataProvider.RepHv(hvData) =>
      log.debug("Received HV grid data.")
      Success(copy(hvGridData = Some(hvData)))
    /* Those states correspond to failed operation */
    case InputDataProvider.OsmReadFailed(reason) =>
      Failure(
        RequestFailedException(
          "The requested OSM data cannot be read. Stop generation",
          reason
        )
      )
  }

  def isComplete: Boolean =
    osmData.isDefined && lvGridData.isDefined && hvGridData.isDefined
}

private object AwaitingMvInputData {
  def empty(mvCoordinatorData: IdleData): AwaitingMvInputData =
    AwaitingMvInputData(
      None,
      None,
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

final case class MvGraph(
)
