/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.apache.pekko.actor.typed.{ActorRef, Behavior, PostStop}
import edu.ie3.osmogrid.ActorStopSupportStateless
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.osmogrid.io.input.{
  BoundaryAdminLevel,
  InputDataEvent,
  ReqAssetTypes,
  ReqOsm,
}
import edu.ie3.osmogrid.lv.LvMessageAdapters.{
  WrappedGridGeneratorResponse,
  WrappedRegionResponse,
}
import edu.ie3.osmogrid.lv.region_coordinator.{
  GridToExpect,
  LvRegionCoordinator,
  Partition,
}
import edu.ie3.osmogrid.model.OsmoGridModel
import edu.ie3.osmogrid.model.SourceFilter.LvFilter
import utils.OsmoGridUtils.buildStreetGraph

import scala.util.{Failure, Success}

/** Actor to take care of the overall generation process for low voltage grids
  */
object LvCoordinator extends ActorStopSupportStateless {
  private var streetGraph = new OsmGraph()

  /** Build a [[LvCoordinator]] with given additional information
    *
    * @param cfg
    *   Config for the generation process
    * @param inputDataProvider
    *   Reference to the [[InputDataProvider]]
    * @param runGuardian
    *   Reference to the [[RunGuardian]] to report to
    * @return
    *   The idle state
    */
  def apply(
      cfg: OsmoGridConfig.Generation.Lv,
      inputDataProvider: ActorRef[InputDataEvent],
      runGuardian: ActorRef[LvResponse],
  ): Behavior[LvRequest] = Behaviors.setup[LvRequest] { context =>
    /* Define message adapters */
    val messageAdapters =
      LvMessageAdapters(
        context.messageAdapter(msg =>
          LvMessageAdapters.WrappedInputDataResponse(msg)
        ),
        context.messageAdapter(msg =>
          LvMessageAdapters.WrappedRegionResponse(msg)
        ),
        context.messageAdapter(msg =>
          LvMessageAdapters.WrappedGridGeneratorResponse(msg)
        ),
      )

    idle(
      IdleData(cfg, inputDataProvider, runGuardian, messageAdapters)
    )
  }

  /** Idle state to receive any kind of [[LvRequest]]
    *
    * @param stateData
    *   Current orientation of the Actor aka. the applicable state data
    * @return
    *   The next state
    */
  private def idle(stateData: IdleData): Behavior[LvRequest] =
    Behaviors
      .receive[LvRequest] {
        case (
              ctx,
              ReqLvGrids,
            ) =>
          ctx.log.info("Starting generation of low voltage grids!")
          ctx.log.debug("Request input data")

          /* Ask for OSM data */
          val filter = stateData.cfg.osm.filter
            .map(cfg =>
              LvFilter(
                cfg.building.toSet,
                cfg.highway.toSet,
                cfg.landuse.toSet,
              )
            )
            .getOrElse(LvFilter())

          stateData.inputDataProvider ! ReqOsm(
            replyTo = stateData.msgAdapters.inputDataProvider,
            filter = filter,
          )
          /* Ask for grid asset data */
          stateData.inputDataProvider ! ReqAssetTypes(
            replyTo = stateData.msgAdapters.inputDataProvider
          )

          /* Change state and await incoming data */
          awaitInputData(
            AwaitingData.empty(stateData)
          )
        case (ctx, LvTerminate) =>
          terminate(ctx.log)
        case (ctx, unsupported) =>
          ctx.log.error(
            s"Received unsupported message '$unsupported' in idle state."
          )
          stopBehavior
      }
      .receiveSignal { case (ctx, PostStop) =>
        postStopCleanUp(ctx.log)
      }

  /** Await incoming input data and register it
    *
    * @param awaitingData
    *   State data for the awaiting
    * @return
    *   Equivalent next state
    */
  private def awaitInputData(
      awaitingData: AwaitingData
  ): Behavior[LvRequest] =
    Behaviors
      .receive[LvRequest] {
        case (ctx, LvMessageAdapters.WrappedInputDataResponse(response)) =>
          /* Register what has been responded */
          awaitingData.registerResponse(response, ctx.log) match {
            case Success(updatedStateData) =>
              handleUpdatedAwaitingData(updatedStateData, ctx)
            case Failure(exception) =>
              ctx.log.error(
                "Request of needed input data failed. Stop low voltage grid generation.",
                exception,
              )
              stopBehavior
          }
        case (ctx, LvTerminate) =>
          terminate(ctx.log)
        case (ctx, unsupported) =>
          ctx.log.warn(
            s"Received unsupported message '$unsupported' in data awaiting state. Keep on going."
          )
          Behaviors.same
      }
      .receiveSignal { case (ctx, PostStop) =>
        postStopCleanUp(ctx.log)
      }

  /** Handle updated [[AwaitingData]]. If everything, that is requested, is at
    * place, spawn child actors and change to Behavior to await results. If
    * still something is missing, wait for it.
    *
    * @param awaitingData
    *   Updated state data
    * @param ctx
    *   Actor context to use
    * @return
    *   Next state
    */
  private def handleUpdatedAwaitingData(
      awaitingData: AwaitingData,
      ctx: ActorContext[LvRequest],
  ): Behavior[LvRequest] = {
    /* Check, if everything is in place */
    if (awaitingData.isComprehensive) {
      /* Process the data */
      ctx.log.debug("All awaited data is present. Start processing.")

      val osmoGridModel = awaitingData.osmData
        .getOrElse(
          throw new RuntimeException("LvOsmoGridModel is missing!")
        )

      val assetInformation = awaitingData.assetInformation.getOrElse(
        throw new RuntimeException("AssetInformation is missing!")
      )

      /* Spawn an coordinator for the region */
      ctx.self ! StartLvGeneration(
        awaitingData.cfg,
        ctx.spawnAnonymous(
          LvRegionCoordinator()
        ),
        osmoGridModel,
        assetInformation,
      )

      /* Wait for results to come up */
      awaitResults(
        awaitingData.guardian,
        awaitingData.msgAdapters,
        ResultData.empty,
      )
    } else awaitInputData(awaitingData) // Wait for missing data
  }

  /** State to receive results from subordinate actors
    *
    * @param guardian
    *   Reference to the guardian actor
    * @param msgAdapters
    *   Collection of all apparent message adapters
    * @return
    *   The next state
    */
  private def awaitResults(
      guardian: ActorRef[LvResponse],
      msgAdapters: LvMessageAdapters,
      resultData: ResultData,
  ): Behavior[LvRequest] =
    Behaviors
      .receive[LvRequest] {
        case (
              ctx,
              StartLvGeneration(
                cfg,
                regionCoordinator,
                osmoGridModel,
                assetInformation,
              ),
            ) =>
          // building a complete street graph
          val (highways, highwayNodes) =
            OsmoGridModel.filterForWays(osmoGridModel.highways)
          streetGraph = buildStreetGraph(highways.seq.toSeq, highwayNodes)

          BoundaryAdminLevel.get(cfg.boundaryAdminLevel.starting) match {
            case Some(startingLevel) =>
              /* Forward the generation request */
              regionCoordinator ! Partition(
                osmoGridModel,
                assetInformation,
                startingLevel,
                cfg,
                msgAdapters.lvRegionCoordinator,
                msgAdapters.lvGridGenerator,
              )
              Behaviors.same
            case None =>
              ctx.log.error(
                s"Cannot parse starting boundary level ${cfg.boundaryAdminLevel.starting}. Shutting down."
              )
              stopBehavior
          }
        case (ctx, WrappedRegionResponse(response)) =>
          response match {
            case GridToExpect(gridUuid) =>
              ctx.log.info(
                s"Expecting grid with UUID: $gridUuid to be generated."
              )

              awaitResults(
                guardian,
                msgAdapters,
                resultData.update(gridUuid),
              )
          }
        case (ctx, WrappedGridGeneratorResponse(response)) =>
          response match {
            case RepLvGrid(gridUuid, subGridContainer) =>
              ctx.log.info(
                s"Received expected grid: $gridUuid"
              )
              val updatedResultData =
                resultData.update(gridUuid, subGridContainer)

              if (updatedResultData.expectedGrids.isEmpty) {
                ctx.log.info(
                  s"Received all expected grids! Will report back SubGridContainers"
                )

                /* Report back the collected grids */
                guardian ! RepLvGrids(
                  updatedResultData.subGridContainers,
                  streetGraph,
                )

                stopBehavior
              } else {
                awaitResults(guardian, msgAdapters, updatedResultData)
              }
          }

        case (ctx, LvTerminate) =>
          terminate(ctx.log)
        case (ctx, unsupported) =>
          ctx.log.error(
            s"Received an unsupported message: '$unsupported'. Shutting down."
          )
          stopBehavior
      }
      .receiveSignal { case (ctx, PostStop) =>
        postStopCleanUp(ctx.log)
      }

  override protected def cleanUp(): Unit = {
    /* Nothing to do here. At least until now. */
  }
}
