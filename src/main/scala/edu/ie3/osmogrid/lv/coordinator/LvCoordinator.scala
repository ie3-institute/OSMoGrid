/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv.coordinator

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, PostStop}
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.osmogrid.{ActorStopSupport, ActorStopSupportStateless}
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.exception.RequestFailedException
import edu.ie3.osmogrid.io.input.InputDataProvider
import edu.ie3.osmogrid.io.input.InputDataProvider.{ReqAssetTypes, ReqOsm}
import edu.ie3.osmogrid.lv.LvRegionCoordinator
import edu.ie3.osmogrid.model.SourceFilter.LvFilter
import edu.ie3.osmogrid.model.OsmoGridModel
import org.slf4j.Logger

import java.util.UUID
import scala.util.{Failure, Success, Try}

/** Actor to take care of the overall generation process for low voltage grids
  */
object LvCoordinator extends ActorStopSupportStateless[Request] {

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
      inputDataProvider: ActorRef[InputDataProvider.InputDataEvent],
      runGuardian: ActorRef[Response]
  ): Behavior[Request] = Behaviors.setup[Request] { context =>
    /* Define message adapters */
    val messageAdapters =
      MessageAdapters(
        context.messageAdapter(msg =>
          MessageAdapters.WrappedInputDataResponse(msg)
        ),
        context.messageAdapter(msg =>
          MessageAdapters.WrappedRegionResponse(msg)
        )
      )

    idle(
      IdleData(cfg, inputDataProvider, runGuardian, messageAdapters)
    )
  }

  /** Idle state to receive any kind of [[Request]]
    *
    * @param stateData
    *   Current orientation of the Actor aka. the applicable state data
    * @return
    *   The next state
    */
  private def idle(stateData: IdleData): Behavior[Request] = Behaviors
    .receive[Request] {
      case (
            ctx,
            ReqLvGrids
          ) =>
        ctx.log.info("Starting generation of low voltage grids!")
        ctx.log.debug("Request input data")

        /* Ask for OSM data */
        val run = UUID.randomUUID()
        val filter = stateData.cfg.osm.filter
          .map(cfg =>
            LvFilter(
              cfg.building.toSet,
              cfg.highway.toSet,
              cfg.landuse.toSet
            )
          )
          .getOrElse(LvFilter())

        stateData.inputDataProvider ! ReqOsm(
          replyTo = stateData.msgAdapters.inputDataProvider,
          filter = filter
        )
        /* Ask for grid asset data */
        stateData.inputDataProvider ! ReqAssetTypes(
          replyTo = stateData.msgAdapters.inputDataProvider
        )

        /* Change state and await incoming data */
        awaitInputData(
          AwaitingData.empty(stateData)
        )
      case (ctx, Terminate) =>
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
  ): Behavior[Request] = Behaviors
    .receive[Request] {
      case (ctx, MessageAdapters.WrappedInputDataResponse(response)) =>
        /* Register what has been responded */
        awaitingData.registerResponse(response, ctx.log) match {
          case Success(updatedStateData) =>
            handleUpdatedAwaitingData(updatedStateData, ctx)
          case Failure(exception) =>
            ctx.log.error(
              "Request of needed input data failed. Stop low voltage grid generation.",
              exception
            )
            stopBehavior
        }
      case (ctx, Terminate) =>
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
      ctx: ActorContext[Request]
  ): Behavior[Request] = {
    /* Check, if everything is in place */
    if (awaitingData.isComprehensive) {
      /* Process the data */
      ctx.log.debug("All awaited data is present. Start processing.")

      /* Spawn an coordinator for the region */
      ctx.self ! StartGeneration(
        awaitingData.cfg,
        ctx.spawnAnonymous(
          LvRegionCoordinator()
        )
      )

      /* Wait for results to come up */
      awaitResults(awaitingData.guardian, awaitingData.msgAdapters)
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
      guardian: ActorRef[Response],
      msgAdapters: MessageAdapters
  ): Behavior[Request] = Behaviors
    .receive[Request] {
      case (ctx, StartGeneration(cfg, regionCoordinator)) =>
        /* Forward the generation request */
        regionCoordinator ! LvRegionCoordinator.Partition(
          cfg,
          msgAdapters.regionCoordinator
        )
        Behaviors.same
      case (
            ctx,
            MessageAdapters.WrappedRegionResponse(
              LvRegionCoordinator.RepLvGrids(subGrids)
            )
          ) =>
        ctx.log.info(
          s"Low voltage grid generation succeeded."
        )

        /* Report back the collected grids */
        guardian ! RepLvGrids(subGrids)

        stopBehavior
      case (ctx, Terminate) =>
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