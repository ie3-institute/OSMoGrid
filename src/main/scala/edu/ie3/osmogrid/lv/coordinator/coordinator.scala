/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv.coordinator

import akka.actor.typed.ActorRef
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.exception.RequestFailedException
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.osmogrid.io.input
import edu.ie3.osmogrid.io.input.AssetInformation
import edu.ie3.osmogrid.lv.LvGridGenerator
import edu.ie3.osmogrid.lv.region_coordinator.LvRegionCoordinator
import edu.ie3.osmogrid.model.OsmoGridModel.LvOsmoGridModel
import org.slf4j.Logger

import scala.util.{Failure, Success, Try}

sealed trait Request

object ReqLvGrids extends Request

/** Request to start the generation of the low voltage grid level for the region
  * of interest
  * @param lvConfig
  *   Configuration for the generation
  * @param regionCoordinator
  *   Reference to the [[LvRegionCoordinator]], to use for region handling
  */
final case class StartGeneration(
    lvConfig: OsmoGridConfig.Generation.Lv,
    regionCoordinator: ActorRef[LvRegionCoordinator.Request],
    osmoGridModel: LvOsmoGridModel,
    assetInformation: AssetInformation
) extends Request

object Terminate extends Request

/** Container class for message adapters
  *
  * @param inputDataProvider
  *   Message adapter for responses from [[InputDataProvider]]
  * @param regionCoordinator
  *   Message adapter for responses from [[LvRegionCoordinator]]
  */
private final case class MessageAdapters(
    inputDataProvider: ActorRef[input.Response],
    lvRegionCoordinator: ActorRef[LvRegionCoordinator.Response],
    lvGridGenerator: ActorRef[LvGridGenerator.Response]
)

private object MessageAdapters {
  final case class WrappedInputDataResponse(
      response: input.Response
  ) extends Request

  final case class WrappedRegionResponse(
      response: LvRegionCoordinator.Response
  ) extends Request

  final case class WrappedGridGeneratorResponse(
      response: LvGridGenerator.Response
  ) extends Request
}

sealed trait Response

/** Replying the generated low voltage grids
  *
  * @param grids
  *   Collection of low voltage grids
  * @param streetGraph
  *   [[OsmGraph]] of the streets
  */
final case class RepLvGrids(grids: Seq[SubGridContainer], streetGraph: OsmGraph)
    extends Response

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
private final case class IdleData(
    cfg: OsmoGridConfig.Generation.Lv,
    inputDataProvider: ActorRef[input.InputDataEvent],
    runGuardian: ActorRef[Response],
    msgAdapters: MessageAdapters
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
private final case class AwaitingData(
    osmData: Option[LvOsmoGridModel],
    assetInformation: Option[input.AssetInformation],
    cfg: OsmoGridConfig.Generation.Lv,
    msgAdapters: MessageAdapters,
    guardian: ActorRef[Response]
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
      response: input.Response,
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

private object AwaitingData {
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
