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
import edu.ie3.osmogrid.io.input.InputDataProvider
import edu.ie3.osmogrid.io.input.InputDataProvider.{
  ReqHvAssets,
  ReqLvAssets,
  ReqOsm
}
import edu.ie3.osmogrid.model.SourceFilter.MvFilter
import edu.ie3.osmogrid.mv.VoronoiHelper.VoronoiPolynomial

import scala.util.{Failure, Success}

object MvCoordinator extends ActorStopSupportStateless {
  private val mvGrids: List[SubGridContainer] = List.empty

  def apply(
      cfg: OsmoGridConfig.Generation.Mv,
      inputDataProvider: ActorRef[InputDataProvider.InputDataEvent],
      runGuardian: ActorRef[MvResponse]
  ): Behavior[MvRequest] = Behaviors.setup[MvRequest] { context =>
    /* Define message adapters */
    val mvMessageAdapters = MvMessageAdapters(
      context.messageAdapter(msg =>
        MvMessageAdapters.WrappedInputDataResponse(msg)
      )
    )

    idle(IdleData(cfg, inputDataProvider, runGuardian, mvMessageAdapters))
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

        stateData.inputDataProvider ! ReqOsm(
          replyTo = replyToSelf,
          filter = filter
        )
        /* Ask for lv grid data */
        stateData.inputDataProvider ! ReqLvAssets(
          replyTo = replyToSelf
        )
        /* Ask for hv grid data */
        stateData.inputDataProvider ! ReqHvAssets(
          replyTo = replyToSelf
        )

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
      awaitingMvData: AwaitingMvInputData,
      ctx: ActorContext[MvRequest]
  ): Behavior[MvRequest] = {
    /* Check, if needed data was received */
    if (awaitingMvData.isComplete) {
      /* Process the data */
      ctx.log.debug("All awaited mv data is present. Start processing.")

      val osmGridModel = awaitingMvData.osmData.getOrElse(
        throw MissingInputDataException("MvOsmoGridModel is missing!")
      )

      val lvGrids = awaitingMvData.lvGridData.getOrElse(
        throw MissingInputDataException("Lv grid data is missing!")
      )

      val hvGrids = awaitingMvData.hvGridData.getOrElse(
        throw MissingInputDataException("Hv grid data is missing!")
      )

      ctx.self ! StartMvGeneration(
        awaitingMvData.cfg,
        lvGrids,
        hvGrids,
        osmGridModel
      )

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
      case (ctx, StartMvGeneration(cfg, lvGrids, hvGrids, osmGridModel)) =>
        /* calculates all voronoi polynomials */
        val polynomials: List[VoronoiPolynomial] =
          VoronoiHelper.calculateVoronoiPolynomials(lvGrids, hvGrids, cfg, ctx)

        Behaviors.same
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

  private def awaitMvGraphResults(
      awaitingMvGraphData: AwaitingMvGraphData
  ): Behavior[MvRequest] = Behaviors
    .receive[MvRequest] {
      case (ctx, FinishedMvGraph(mvGraph)) =>
        ctx.log.debug(
          s"Received mv graph #${awaitingMvGraphData.completed + 1} (${awaitingMvGraphData.uncompleted - 1} to go)."
        )

        val updatedData = awaitingMvGraphData
          .copy(graphs = awaitingMvGraphData.graphs :+ mvGraph)

        if (updatedData.isComplete) {
          ctx.self ! StartMvGraphConversion(
            awaitingMvGraphData.cfg,
            awaitingMvGraphData.graphs
          )
          convertGraphToGrid(
            awaitingMvGraphData.guardian,
            awaitingMvGraphData.msgAdapters
          )
        } else awaitMvGraphResults(updatedData)
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

  // should receive mv graph
  // should call MvGridBuilder
  // should wait for mv grid
  // should send mv grid to awaitResult
  private def convertGraphToGrid(
      guardian: ActorRef[MvResponse],
      msgAdapters: MvMessageAdapters
  ): Behavior[MvRequest] = ???

  // should wait for all mv graphs
  // should call convertGraphToGrid for all mv graphs
  // should wait for all mv grids
  // should send all results to sendResultsToGuardian
  private def awaitResults(): Behavior[MvResponse] = ???

  // should send all mv grids to guardian
  private def sendResultsToGuardian(): Behavior[MvRequest] = ???

  override protected def cleanUp(): Unit = {
    /* Nothing to do here. At least until now. */
  }
}
