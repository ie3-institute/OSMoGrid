/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import akka.actor.typed.{ActorRef, Behavior, PostStop, SupervisorStrategy}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, Routers}
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.cfg.OsmoGridConfig.Generation.Lv
import edu.ie3.osmogrid.io.input.InputDataProvider
import edu.ie3.osmogrid.lv.LvCoordinator.cleanUp
import edu.ie3.osmogrid.lv.LvGridGenerator
import org.slf4j.Logger

import java.util.UUID

/** Actor to take care of the overall generation process for low voltage grids
  */
object LvCoordinator {
  sealed trait Request

  final case class ReqLvGrids(
      cfg: OsmoGridConfig.Generation.Lv,
      replyTo: ActorRef[Response]
  ) extends Request

  object Terminate extends Request

  /* Container class for message adapters as well as wrapping classes themselves */

  /** Container class for message adapters
    *
    * @param inputDataProvider
    *   Message adapter for responses from [[InputDataProvider]]
    * @param regionCoordinator
    *   Message adapter for responses from [[LvRegionCoordinator]]
    */
  private final case class MessageAdapters(
      inputDataProvider: ActorRef[InputDataProvider.Response],
      regionCoordinator: ActorRef[LvRegionCoordinator.Response]
  )

  private object MessageAdapters {
    final case class WrappedInputDataResponse(
        response: InputDataProvider.Response
    ) extends Request

    final case class WrappedRegionResponse(
        response: LvRegionCoordinator.Response
    ) extends Request
  }

  sealed trait Response

  /** Replying the generated low voltage grids
    *
    * @param grids
    *   Collection of low voltage grids
    */
  final case class RepLvGrids(grids: Seq[SubGridContainer]) extends Response

  def apply(): Behavior[Request] = Behaviors.setup[Request] { context =>
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

    idle(messageAdapters)
  }

  /** Idle state to receive any kind of [[Request]]
    *
    * @param msgAdapters
    *   Available message adapters to use, when defining to where to reply
    * @return
    *   The next state
    */
  private def idle(msgAdapters: MessageAdapters): Behavior[Request] = Behaviors
    .receive[Request] {
      case (
            ctx,
            ReqLvGrids(
              Lv(
                distinctHouseConnections
              ),
              replyTo
            )
          ) =>
        ctx.log.info("Starting generation of low voltage grids!")
        ctx.log.debug("Request input data")
        /* TODO:
              1) Ask for OSM data
              2) Ask for asset data */

        /* Change state and await incoming data */
        awaitInputData(AwaitingData.empty(msgAdapters, replyTo))
      case (ctx, unsupported) =>
        ctx.log.error(
          s"Received unsupported message '$unsupported' in idle state."
        )
        stopState
    }
    .receiveSignal { case (ctx, PostStop) =>
      postStopCleanUp(ctx.log)
    }

  /** State data to describe the actor's orientation while awaiting replies
    *
    * @param osmData
    *   Current state of information for open street maps data
    * @param assetData
    *   Current state of information for asset data
    * @param msgAdapters
    *   Collection of available message adapters
    * @param guardian
    *   Reference to the guardian actor
    */
  private final case class AwaitingData(
      osmData: Option[Int],
      assetData: Option[Int],
      msgAdapters: MessageAdapters,
      guardian: ActorRef[Response]
  )

  private object AwaitingData {
    def empty(
        msgAdapters: MessageAdapters,
        guardian: ActorRef[Response]
    ): AwaitingData = AwaitingData(None, None, msgAdapters, guardian)
  }

  /** Await incoming input data and register it
    *
    * TODO: Adapt accordingly to have classes that collect the data
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
        /* TODO: Register data according to message content */

        /* Check, if everything is in place */
        if (
          awaitingData.osmData.isDefined && awaitingData.assetData.isDefined
        ) {
          /* Process the data */
          ctx.log.debug("All awaited data is present. Start processing.")

          /* Spawn an coordinator for the region */
          val lvRegionCoordinator = ctx.spawn(
            LvRegionCoordinator(),
            "LvRegionCoordinator"
          ) // TODO: Add run id to name
          lvRegionCoordinator ! LvRegionCoordinator.Partition(
            ctx.messageAdapter(msg =>
              MessageAdapters.WrappedRegionResponse(msg)
            )
          )

          /* Wait for results to come up */
          awaitResults(awaitingData.guardian)
        } else
          Behaviors.same // Wait for missing data
      case (ctx, unsupported) =>
        ctx.log.warn(
          s"Received unsupported message '$unsupported' in data awaiting state. Keep on going."
        )
        Behaviors.same
    }
    .receiveSignal { case (ctx, PostStop) =>
      postStopCleanUp(ctx.log)
    }

  /** State to receive results from subordinate actors
    *
    * @param guardian
    *   Reference to the guardian actor
    * @return
    *   The next state
    */
  private def awaitResults(
      guardian: ActorRef[Response]
  ): Behavior[Request] = Behaviors
    .receive[Request] {
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

        stopState
      case (ctx, unsupported) =>
        ctx.log.error(
          s"Received an unsupported message: '$unsupported'. Shutting down."
        )
        stopState
    }
    .receiveSignal { case (ctx, PostStop) =>
      postStopCleanUp(ctx.log)
    }

  /** Do clean-up after (forced) stop has been issued.
    *
    * @param log
    *   Logger to use
    * @return
    *   The next state
    */
  private def postStopCleanUp(log: Logger): Behavior[Request] = {
    log.info("Got terminated by ActorSystem.")
    stopState
  }

  /** Partial function to perform cleanup tasks while shutting down
    */
  private val cleanUp: () => Unit = ???

  /** Specific stop state with clean up actions issued
    */
  private val stopState: Behavior[Request] = Behaviors.stopped(cleanUp)
}
