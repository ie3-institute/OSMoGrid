/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import org.apache.pekko.actor.typed.ActorRef
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.exception.{
  IllegalStateException,
  RequestFailedException
}
import edu.ie3.osmogrid.exception.RequestFailedException
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.osmogrid.io.input
import edu.ie3.osmogrid.io.input.{AssetInformation, InputResponse}
import edu.ie3.osmogrid.lv.region_coordinator.{
  LvRegionCoordinator,
  LvRegionRequest,
  LvRegionResponse
}
import edu.ie3.osmogrid.model.OsmoGridModel.LvOsmoGridModel
import org.slf4j.Logger

import java.util.UUID
import scala.util.{Failure, Success, Try}

sealed trait LvRequest

sealed trait LvGridRequest

sealed trait MunicipalityRequest

sealed trait DistrictRequest

sealed trait SubDistrictRequest

object ReqLvGrids extends LvRequest

/** Request to start the generation of the low voltage grid level for the region
  * of interest
  * @param lvConfig
  *   Configuration for the generation
  * @param regionCoordinator
  *   Reference to the [[LvRegionCoordinator]], to use for region handling
  */
final case class StartLvGeneration(
    lvConfig: OsmoGridConfig.Generation.Lv,
    regionCoordinator: ActorRef[LvRegionRequest],
    osmoGridModel: LvOsmoGridModel,
    assetInformation: AssetInformation
) extends LvRequest

object LvTerminate extends LvRequest

/** Container class for message adapters
  *
  * @param inputDataProvider
  *   Message adapter for responses from [[InputDataProvider]]
  * @param regionCoordinator
  *   Message adapter for responses from [[LvRegionCoordinator]]
  */
private final case class LvMessageAdapters(
    inputDataProvider: ActorRef[InputResponse],
    lvRegionCoordinator: ActorRef[LvRegionResponse],
    lvGridGenerator: ActorRef[LvGridResponse]
)

private object LvMessageAdapters {
  final case class WrappedInputDataResponse(
      response: InputResponse
  ) extends LvRequest

  final case class WrappedRegionResponse(
      response: LvRegionResponse
  ) extends LvRequest

  final case class WrappedGridGeneratorResponse(
      response: LvGridResponse
  ) extends LvRequest
}

final case class GenerateLvGrid(
    replyTo: ActorRef[LvGridResponse],
    gridUuid: UUID,
    osmData: LvOsmoGridModel,
    assetInformation: AssetInformation,
    config: OsmoGridConfig.Generation.Lv
) extends LvGridRequest

sealed trait LvResponse

sealed trait LvGridResponse

/** Replying the generated low voltage grids
  *
  * @param grids
  *   Collection of low voltage grids
  * @param streetGraph
  *   [[OsmGraph]] of the streets
  */
final case class RepLvGrids(grids: Seq[SubGridContainer], streetGraph: OsmGraph)
    extends LvResponse

final case class RepLvGrid(
    gridUuid: UUID,
    grid: Seq[SubGridContainer]
) extends LvGridResponse

/** State data for orientation of the actor
  *
  * @param cfg
  *   Config for the generation process
  * @param inputDataProvider
  *   Reference to the [[InputDataProvider]]
  * @param runGuardian
  *   Reference to the [[RunGuardian]] to report to
  * @param msgAdapters
  *   Collection of all necessary message adapters
  */
private[lv] final case class IdleData(
    cfg: OsmoGridConfig.Generation.Lv,
    inputDataProvider: ActorRef[input.InputDataEvent],
    runGuardian: ActorRef[LvResponse],
    msgAdapters: LvMessageAdapters
)

/** State data to describe the actor's orientation while awaiting replies
  *
  * @param osmData
  *   Current state of information for open street maps data
  * @param assetInformation
  *   Current state of information for asset data
  * @param cfg
  *   Config for the generation process
  * @param msgAdapters
  *   Collection of available message adapters
  * @param guardian
  *   Reference to the guardian actor
  */
private[lv] final case class AwaitingData(
    osmData: Option[LvOsmoGridModel],
    assetInformation: Option[input.AssetInformation],
    cfg: OsmoGridConfig.Generation.Lv,
    msgAdapters: LvMessageAdapters,
    guardian: ActorRef[LvResponse]
) {

  /** Takes the given response and adds the contained value to the currently
    * apparent data. The result is handed back as a trial on the result, where
    * the success contains an adapted copy of the current instance.
    *
    * @param response
    *   The response to obtain data from
    * @param log
    *   Instance of a logger to use
    * @return
    *   a [[Try]] onto an adapted copy of the current instance
    */
  def registerResponse(
      response: InputResponse,
      log: Logger
  ): Try[AwaitingData] = response match {
    case input.RepOsm(osmModel: LvOsmoGridModel) =>
      log.debug(s"Received LV data model.")
      Success(copy(osmData = Some(osmModel)))
    case input.RepAssetTypes(assetInformation) =>
      log.debug(s"Received asset information.")
      Success(
        copy(assetInformation = Some(assetInformation))
      )
    /* Those states correspond to failed operation */
    case input.OsmReadFailed(reason) =>
      Failure(
        RequestFailedException(
          "The requested OSM data cannot be read. Stop generation. Exception:",
          reason
        )
      )
    case input.AssetReadFailed(reason) =>
      Failure(
        RequestFailedException(
          "The requested asset data cannot be read. Stop generation. Exception:",
          reason
        )
      )
  }

  def isComprehensive: Boolean =
    osmData.isDefined && assetInformation.isDefined

}

private[lv] object AwaitingData {
  def empty(
      coordinatorData: IdleData
  ): AwaitingData = AwaitingData(
    None,
    None,
    coordinatorData.cfg,
    coordinatorData.msgAdapters,
    coordinatorData.runGuardian
  )
}

private[lv] final case class ResultData(
    expectedGrids: Set[UUID],
    subGridContainers: Seq[SubGridContainer]
) {

  def update(expectedGrid: UUID): ResultData = {
    ResultData(expectedGrids + expectedGrid, subGridContainers)
  }

  def update(
      expectedGrid: UUID,
      subGridContainer: Seq[SubGridContainer]
  ): ResultData = {
    if (expectedGrids.contains(expectedGrid)) {
      return ResultData(
        expectedGrids - expectedGrid,
        subGridContainers ++ subGridContainer
      )
    }
    throw IllegalStateException(
      s"Trying to update with subgrid container that was not expected. UUID: $expectedGrid"
    )
  }
}

private[lv] object ResultData {
  def empty: ResultData = {
    ResultData(Set.empty[UUID], Seq.empty[SubGridContainer])
  }
}
