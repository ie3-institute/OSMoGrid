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
import utils.{SubGridContainerUtils, VoronoiUtils}

import scala.util.{Failure, Success}

object MvCoordinator extends ActorStopSupportStateless {
  private val mvGrids: List[SubGridContainer] = List.empty

  def apply(
      cfg: OsmoGridConfig.Generation.Mv,
      inputDataProvider: ActorRef[InputDataEvent],
      runGuardian: ActorRef[MvResponse]
  ): Behavior[MvRequest] = Behaviors.setup[MvRequest] { context =>
    /* Define message adapters */

    /*
    val mvMessageAdapters = MvMessageAdapters(
      context.messageAdapter(msg =>
        MvMessageAdapters.WrappedInputDataResponse(msg)
      )
    )

    idle(IdleData(cfg, inputDataProvider, runGuardian, mvMessageAdapters))

     */
    ???
  }

  // should receive request to generate and return mv grids
  // should call awaitInputData
  private def idle(stateData: IdleData): Behavior[MvRequest] = Behaviors
    .receive[MvRequest] {
      case (ctx, ReqMvGrids) =>
        ctx.log.info("Starting generation of medium voltage grids!")
        ctx.log.debug("Request input data.")

        /* Ask for OSM data */
        val filter = stateData.cfg.osm.filter
          .map(cfg =>
            MvFilter(
              cfg.building.toSet,
              cfg.highway.toSet,
              cfg.landuse.toSet
            )
          )
          .getOrElse(MvFilter())

        val replyToSelf = stateData.msgAdapter.inputDataProvider

        /*
        stateData.inputDataProvider ! ReqOsm(
          replyTo = replyToSelf,
          filter = filter
        )
         */

        /* Change state and await incoming data */
        awaitInputData(
          AwaitingMvInputData.empty(stateData)
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
      awaitingMvData: AwaitingMvInputData
  ): Behavior[MvRequest] =
    Behaviors
      .receive[MvRequest] {
        case (ctx, MvMessageAdapters.WrappedInputDataResponse(response)) =>
          /*
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
           */
          ???
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
      awaitingMvData: AwaitingMvInputData,
      ctx: ActorContext[MvRequest]
  ): Behavior[MvRequest] = {
    /* Check, if needed data was received */
    if (awaitingMvData.isComplete) {
      /* Process the data */
      ctx.log.debug("All awaited mv data is present. Start processing.")

      // TODO: Fix this!
      /*
      val osmGridModel = awaitingMvData.osmData.getOrElse(
        throw MissingInputDataException("MvOsmoGridModel is missing!")
      )

      ctx.self ! StartMvGeneration(
        awaitingMvData.cfg,
        lvGrids = null,
        hvGrids = null,
        osmGridModel
      )
       */

      processInputData(awaitingMvData.guardian, awaitingMvData.msgAdapters)
    } else awaitInputData(awaitingMvData) // Continue waiting for missing data
  }

  // should start voronoi algorithm
  // should start VoronoiCoordinators (one for each voronoi polynomial)
  // should call awaitMvGraphResults
  private def processInputData(
      guardian: ActorRef[MvResponse],
      msgAdapters: MvMessageAdapters
  ): Behavior[MvRequest] = Behaviors
    .receive[MvRequest] {
      case (ctx, StartMvGeneration(cfg, lvGrids, hvGrids, streetGraph)) =>
        /* calculates all voronoi polynomials */
        val (hvToMv, mvToLv) =
          SubGridContainerUtils.filter(lvGrids, hvGrids, cfg)
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
        awaitResults()
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

  // should wait for all mv graphs
  // should call convertGraphToGrid for all mv graphs
  // should wait for all mv grids
  // should send all results to sendResultsToGuardian
  private def awaitResults(): Behavior[MvRequest] = ???

  // should send all mv grids to guardian
  private def sendResultsToGuardian(): Behavior[MvRequest] = ???

  override protected def cleanUp(): Unit = {
    /* Nothing to do here. At least until now. */
  }
}
