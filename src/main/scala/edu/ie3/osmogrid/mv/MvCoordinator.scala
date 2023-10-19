/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.mv

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, PostStop}
import edu.ie3.osmogrid.ActorStopSupportStateless
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.guardian.run.RunGuardian
import edu.ie3.osmogrid.io.input
import edu.ie3.osmogrid.io.input.{InputDataEvent, ReqAssetTypes}
import edu.ie3.osmogrid.mv.MvMessageAdapters.WrappedInputResponse
import utils.OsmoGridUtils.spawnDummyHvNode
import utils.{GridContainerUtils, VoronoiUtils}

import scala.util.{Failure, Success}

/** Actor to take care of the overall generation process for medium voltage
  * grids.
  */
object MvCoordinator extends ActorStopSupportStateless {

  /** Build a [[MvCoordinator]] with given information.
    * @param cfg
    *   config for the generation process
    * @param inputDataProvider
    *   reference to the [[InputDataProvider]]
    * @param runGuardian
    *   reference to the [[RunGuardian]] to report to
    * @return
    *   the idle state
    */
  def apply(
      cfg: OsmoGridConfig.Generation.Mv,
      inputDataProvider: ActorRef[InputDataEvent],
      runGuardian: ActorRef[MvResponse]
  ): Behavior[MvRequest] = Behaviors.setup[MvRequest] { context =>
    val messageAdapters = MvMessageAdapters(
      context.messageAdapter(msg => WrappedInputResponse(msg))
    )

    idle(cfg, inputDataProvider, messageAdapters, runGuardian)
  }

  /** Idle behaviour of the [[MvCoordinator]].
    * @param cfg
    *   medium voltage generation config
    * @param runGuardian
    *   superior actor
    * @return
    *   a new behaviour
    */
  private def idle(
      cfg: OsmoGridConfig.Generation.Mv,
      inputDataProvider: ActorRef[InputDataEvent],
      messageAdapters: MvMessageAdapters,
      runGuardian: ActorRef[MvResponse]
  ): Behavior[MvRequest] = Behaviors
    .receive[MvRequest] {
      case (ctx, ReqMvGrids) =>
        ctx.log.info("Starting generation of medium voltage grids!")
        ctx.log.debug("Waiting for input data.")

        inputDataProvider ! ReqAssetTypes(
          replyTo = messageAdapters.inputDataProvider
        )

        /* Change state and await incoming data */
        awaitInputData(
          AwaitingInputData.empty(cfg, runGuardian)
        )
      case (ctx, MvTerminate) =>
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

  /** State for awaiting the necessary input data.
    * @param awaitingMvData
    *   object containing the currently received input data
    * @return
    *   a new behaviour
    */
  private def awaitInputData(
      awaitingMvData: AwaitingInputData
  ): Behavior[MvRequest] =
    Behaviors
      .receive[MvRequest] {
        case (ctx, response: WrappedInputResponse) =>
          awaitingMvData.registerResponse(response, ctx.log) match {
            case Success(updatedStateData) =>
              handleUpdatedStateData(updatedStateData, ctx)
            case Failure(exception) =>
              ctx.log.error(
                "Request of needed input data failed. Stop medium voltage grid generation.",
                exception
              )
              stopBehavior
          }
        case (ctx, response: WrappedMvResponse) =>
          awaitingMvData.registerResponse(response, ctx.log) match {
            case Success(updatedStateData) =>
              handleUpdatedStateData(updatedStateData, ctx)
            case Failure(exception) =>
              ctx.log.error(
                "Request of needed input data failed. Stop medium voltage grid generation.",
                exception
              )
              stopBehavior
          }
        case (ctx, MvTerminate) =>
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

  // should handle received data
  // if all required data is present call processInputData

  /** Method for handling the state data update.
    * @param awaitingInputData
    *   object containing the currently received input data
    * @param ctx
    *   actor context
    * @return
    *   a new behaviour
    */
  private def handleUpdatedStateData(
      awaitingInputData: AwaitingInputData,
      ctx: ActorContext[MvRequest]
  ): Behavior[MvRequest] = {
    /* Check, if needed data was received */
    if (awaitingInputData.isComprehensive) {
      /* Process the data */
      ctx.log.debug("All awaited mv data is present. Start processing.")

      val (lvGrids, hvGrids, streetGraph, assetInformation) =
        awaitingInputData match {
          case AwaitingInputData(
                _,
                _,
                Some(lvGrids),
                hvGrids,
                Some(streetGraph),
                Some(assetInformation)
              ) =>
            (lvGrids, hvGrids, streetGraph, assetInformation)
        }

      ctx.self ! StartGeneration(
        lvGrids,
        hvGrids,
        streetGraph,
        assetInformation
      )

      startGraphGeneration(awaitingInputData.cfg, awaitingInputData.runGuardian)
    } else
      awaitInputData(awaitingInputData) // Continue waiting for missing data
  }

  /** State for generating the medium voltage graph.
    *
    * @param cfg
    *   medium voltage generation config
    * @param runGuardian
    *   superior actor
    * @return
    *   the [[awaitResults]] state
    */
  private def startGraphGeneration(
      cfg: OsmoGridConfig.Generation.Mv,
      runGuardian: ActorRef[MvResponse]
  ): Behavior[MvRequest] = Behaviors
    .receive[MvRequest] {
      case (
            ctx,
            StartGeneration(lvGrids, hvGrids, streetGraph, assetInformation)
          ) =>
        ctx.log.debug(s"Starting medium voltage graph generation.")

        // collect all mv node from lv sub grid containers
        val mvToLv = GridContainerUtils.filterLv(lvGrids)

        // collect all mv nodes from hv sub grid container or spawn a new mv node
        val hvToMv = hvGrids match {
          case Some(grids) =>
            GridContainerUtils.filterHv(grids)
          case None => List(spawnDummyHvNode(mvToLv))
        }

        val (polygons, notAssignedNodes) =
          VoronoiUtils.createVoronoiPolygons(hvToMv.toList, mvToLv.toList, ctx)

        ctx.log.debug(s"Given area was split into ${polygons.size} polygon(s).")

        if (notAssignedNodes.nonEmpty) {
          ctx.log.warn(
            s"Found ${notAssignedNodes.size} nodes, that are not inside a voronoi polygon!"
          )
        }

        // spawns a voronoi coordinator for each polygon
        val subnets: Set[Int] = polygons.zipWithIndex.map {
          case (polygon, index) =>
            val nr: Int = 100 + index * 20

            val voronoiCoordinator: ActorRef[MvRequest] =
              ctx.spawnAnonymous(VoronoiCoordinator(ctx.self))
            voronoiCoordinator ! StartGraphGeneration(
              nr,
              polygon,
              streetGraph,
              assetInformation,
              cfg
            )

            nr
        }.toSet

        // awaiting the psdm grid data
        awaitResults(runGuardian, MvResultData.empty(subnets))
      case (ctx, MvTerminate) =>
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

  // should wait for all mv grids
  // should send all results to run guardian
  /** State for awaiting the medium voltage grids. This method will send the
    * data to the [[RunGuardian]] after receiving all data.
    * @param runGuardian
    *   superior actor
    * @param resultData
    *   until now received data
    */
  private def awaitResults(
      runGuardian: ActorRef[MvResponse],
      resultData: MvResultData
  ): Behavior[MvRequest] = Behaviors
    .receive[MvRequest] {
      case (
            ctx,
            WrappedMvResponse(
              FinishedMvGridData(
                subGridContainer,
                nodeChanges
              )
            )
          ) =>
        // updating the result data
        val updated =
          resultData.update(subGridContainer, nodeChanges)

        // if all sub grids are received, send a message to the run guardian
        if (updated.subnets.isEmpty) {
          ctx.log.info(
            s"Received all expected grids! Will report back SubGridContainers"
          )

          runGuardian ! RepMvGrids(
            updated.subGridContainer,
            updated.nodes
          )
          Behaviors.stopped
        } else {
          awaitResults(runGuardian, updated)
        }
      case (ctx, MvTerminate) =>
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

  override protected def cleanUp(): Unit = {
    /* Nothing to do here. At least until now. */
  }
}
