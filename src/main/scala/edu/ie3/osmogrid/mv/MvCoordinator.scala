/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.mv

import akka.actor.typed.{ActorRef, Behavior, PostStop}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.osmogrid.ActorStopSupportStateless
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.exception.MissingInputDataException
import edu.ie3.osmogrid.io.input.{InputDataEvent, InputDataProvider}
import edu.ie3.osmogrid.model.SourceFilter.MvFilter
import utils.OsmoGridUtils.spawnDummyHvNode
import utils.{OsmoGridUtils, SubGridContainerUtils, VoronoiUtils}

import scala.util.{Failure, Success}

/** Actor to take care of the overall generation process for medium voltage
  * grids.
  */
object MvCoordinator extends ActorStopSupportStateless {

  /** Build a [[MvCoordinator]] with given information.
    * @param cfg
    *   config for the generation process
    * @param runGuardian
    *   reference to the [[RunGuardian]] to report to
    * @return
    *   the idle state
    */
  def apply(
      cfg: OsmoGridConfig.Generation.Mv,
      runGuardian: ActorRef[MvResponse]
  ): Behavior[MvRequest] = idle(cfg, runGuardian)

  // should receive request to generate and return mv grids
  // should call awaitInputData
  private def idle(
      cfg: OsmoGridConfig.Generation.Mv,
      runGuardian: ActorRef[MvResponse]
  ): Behavior[MvRequest] = Behaviors
    .receive[MvRequest] {
      case (ctx, ReqMvGrids) =>
        ctx.log.info("Starting generation of medium voltage grids!")
        ctx.log.debug("Waiting for input data.")

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

  // should wait for input data (Lv SubGridContainer, Hv SubGridContainer, ...)
  // should call handleUpdatedStateData
  private def awaitInputData(
      awaitingMvData: AwaitingInputData
  ): Behavior[MvRequest] =
    Behaviors
      .receive[MvRequest] {
        case (ctx, WrappedMvResponse(response)) =>
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
  private def handleUpdatedStateData(
      awaitingInputData: AwaitingInputData,
      ctx: ActorContext[MvRequest]
  ): Behavior[MvRequest] = {
    /* Check, if needed data was received */
    if (awaitingInputData.isComprehensive) {
      /* Process the data */
      ctx.log.debug("All awaited mv data is present. Start processing.")

      val (lvGrids, hvGrids, streetGraph) = awaitingInputData match {
        case AwaitingInputData(
              _,
              _,
              Some(lvGrids),
              hvGrids,
              Some(streetGraph)
            ) =>
          (lvGrids, hvGrids, streetGraph)
      }

      ctx.self ! StartGeneration(lvGrids, hvGrids, streetGraph)

      startGraphGeneration(awaitingInputData.cfg, awaitingInputData.runGuardian)
    } else
      awaitInputData(awaitingInputData) // Continue waiting for missing data
  }

  // should start voronoi algorithm
  // should start VoronoiCoordinators (one for each voronoi polynomial)
  // should call awaitMvGraphResults
  private def startGraphGeneration(
      cfg: OsmoGridConfig.Generation.Mv,
      runGuardian: ActorRef[MvResponse]
  ): Behavior[MvRequest] = Behaviors
    .receive[MvRequest] {
      case (ctx, StartGeneration(lvGrids, hvGrids, streetGraph)) =>
        /* calculates all voronoi polynomials */

        // collect all mv node from lv sub grid containers
        val mvToLv = SubGridContainerUtils.filterLv(lvGrids, cfg)

        // collect all mv nodes from hv sub grid container or spawn a new mv node
        val hvToMv = hvGrids match {
          case Some(grids) =>
            SubGridContainerUtils.filterHv(grids, cfg)
          case None => List(spawnDummyHvNode(mvToLv))
        }

        val (polygons, notAssignedNodes) =
          VoronoiUtils.createVoronoiPolygons(hvToMv, mvToLv, ctx)

        if (notAssignedNodes.nonEmpty) {
          ctx.log.warn(
            s"Found ${notAssignedNodes.size} nodes, that are not inside a voronoi polygon!"
          )
        }

        // spawns a voronoi coordinator for each polygon
        polygons.zipWithIndex.foreach { case (polygon, index) =>
          val nr: Int = 100 + index * 20

          val voronoiCoordinator: ActorRef[MvRequest] =
            ctx.spawnAnonymous(VoronoiCoordinator(ctx.self))
          voronoiCoordinator ! StartGraphGeneration(
            nr,
            polygon,
            streetGraph,
            cfg
          )
        }

        // awaiting the psdm grid data
        awaitResults(runGuardian)
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
  // should send all results to sendResultsToGuardian
  private def awaitResults(
      runGuardian: ActorRef[MvResponse]
  ): Behavior[MvRequest] = ???

  // should send all mv grids to guardian
  private def sendResultsToGuardian(): Behavior[MvRequest] = ???

  override protected def cleanUp(): Unit = {
    /* Nothing to do here. At least until now. */
  }
}
