/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.mv

import edu.ie3.datamodel.models.input.NodeInput
import edu.ie3.datamodel.models.input.container.JointGridContainer
import edu.ie3.osmogrid.ActorStopSupportStateless
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.guardian.run.RunGuardian
import edu.ie3.osmogrid.io.input.{InputDataEvent, ReqAssetTypes}
import edu.ie3.osmogrid.mv.MvMessageAdapters.WrappedInputResponse
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.apache.pekko.actor.typed.{ActorRef, Behavior, PostStop}
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
    *   reference to the [[edu.ie3.osmogrid.io.input.InputDataProvider]]
    * @param runGuardian
    *   reference to the [[RunGuardian]] to report to
    * @return
    *   the idle state
    */
  def apply(
      cfg: OsmoGridConfig,
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
      cfg: OsmoGridConfig,
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
    if (awaitingInputData.isComplete) {
      /* Process the data */
      ctx.log.debug("All awaited mv data is present. Start processing.")

      val lvGrids = awaitingInputData.lvGrids.getOrElse(
        throw new RuntimeException("Lv grids are missing!")
      )

      // checking hv nodes
      val hvGrids = (
        awaitingInputData.hvGrids,
        awaitingInputData.cfg.spawnMissingHvNodes
      ) match {
        case (Some(hv), true) =>
          ctx.log.info(
            "Spawn missing hv nodes is set to true, while hv grids were provided. Config setting is ignored!"
          )
          Some(hv)
        case (Some(hv), false) => Some(hv)
        case (None, false) =>
          if (awaitingInputData.hvGridsRequired) {
            throw new RuntimeException("Hv grids are needed and missing!")
          } else None
        case (None, true) =>
          ctx.log.debug(
            "No hv grids are provided! A new hv node will be spawned."
          )
          None
      }

      val streetGraph = awaitingInputData.streetGraph.getOrElse(
        throw new RuntimeException("Street graph is missing!")
      )

      val assetInformation = awaitingInputData.assetInformation.getOrElse(
        throw new RuntimeException("Asset information are missing!")
      )

      ctx.self ! StartMvGeneration(
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
            StartMvGeneration(lvGrids, hvGrids, streetGraph, assetInformation)
          ) =>
        ctx.log.debug(s"Starting medium voltage graph generation.")

        // collect all mv node from lv sub grid containers
        val mvToLv = GridContainerUtils.filterLv(lvGrids)

        // collect all mv nodes from hv sub grid container or spawn a new mv node
        val hvOption = hvGrids.map(GridContainerUtils.filterHv)

        // if no hv subgrid container were provided or no mv nodes could be extracted
        // check if spawning a dummy node is activated
        val hvToMv: Option[(JointGridContainer, NodeInput)] =
          Option.when(hvOption.isEmpty && cfg.spawnMissingHvNodes)(
            spawnDummyHvNode(mvToLv, assetInformation)
          )

        // get the mv transition nodes
        val (transitionNodes, uuidOption) = (hvOption, hvToMv) match {
          case (Some(nodeList), _) =>
            // found mv nodes from hv containers
            (nodeList, None)
          case (None, Some(value)) =>
            // using a mv node with a spawned dummy hv node
            (List(value._2), None)
          case (None, None) =>
            // using a mv node with no dummy hv node
            val mvSlackNode = mvToLv(0).copy().slack(true).build()
            (hvOption.getOrElse(List(mvSlackNode)), Some(mvSlackNode.getUuid))
        }

        val (polygons, notAssignedNodes) =
          VoronoiUtils.createVoronoiPolygons(
            transitionNodes.toList,
            mvToLv.toList,
            ctx
          )

        ctx.log.info(s"Given area was split into ${polygons.size} polygon(s).")

        if (notAssignedNodes.nonEmpty) {
          ctx.log.warn(
            s"Found ${notAssignedNodes.size} nodes, that are not inside a voronoi polygon!"
          )
        }

        // spawns a voronoi coordinator for each polygon
        val subnets: Set[Int] = polygons.zipWithIndex.map {
          case (polygon, index) =>
            val voronoiCoordinator: ActorRef[MvRequest] =
              ctx.spawnAnonymous(VoronoiCoordinator(ctx.self))
            voronoiCoordinator ! StartMvGraphGeneration(
              index + 1,
              polygon,
              uuidOption,
              streetGraph,
              assetInformation
            )

            index + 1
        }.toSet

        // awaiting the psdm grid data
        awaitResults(
          runGuardian,
          MvResultData.empty(subnets, hvToMv.map(_._1), assetInformation)
        )
      case (ctx, MvTerminate) =>
        terminate(ctx.log)
      case (ctx, unsupported) =>
        ctx.log.warn(
          s"Received unsupported message '$unsupported' in start graph generation state. Keep on going."
        )
        Behaviors.same
    }
    .receiveSignal { case (ctx, PostStop) =>
      postStopCleanUp(ctx.log)
    }

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
            updated.dummyHvGrid,
            updated.nodes,
            resultData.assetInformation
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
