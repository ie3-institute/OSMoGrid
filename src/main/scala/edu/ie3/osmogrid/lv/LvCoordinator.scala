/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import akka.actor.typed.{ActorRef, Behavior, PostStop, SupervisorStrategy}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, Routers}
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.osmogrid.ActorStopSupport
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.cfg.OsmoGridConfig.Generation.Lv
import edu.ie3.osmogrid.exception.RequestFailedException
import edu.ie3.osmogrid.io.input.InputDataProvider
import edu.ie3.osmogrid.io.input.InputDataProvider.{ReqAssetTypes, ReqOsm}
import edu.ie3.osmogrid.lv.LvCoordinator.{Request, cleanUp}
import edu.ie3.osmogrid.lv.LvGridGenerator
import edu.ie3.osmogrid.model.{OsmoGridModel, PbfFilter}
import org.slf4j.Logger

import java.util.UUID
import javax.xml.transform.dom.DOMSource
import scala.util.{Failure, Success, Try}

/** Actor to take care of the overall generation process for low voltage grids
  */
object LvCoordinator extends ActorStopSupport[Request] {
  sealed trait Request

  final case class ReqLvGrids(
      inputDataProvider: ActorRef[InputDataProvider.Request],
      cfg: OsmoGridConfig.Generation.Lv,
      replyTo: ActorRef[Response]
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
              inputDataProvider,
              lvConfig,
              replyTo
            )
          ) =>
        ctx.log.info("Starting generation of low voltage grids!")
        ctx.log.debug("Request input data")

        /* Ask for OSM data */
        inputDataProvider ! ReqOsm(
          runId = UUID.randomUUID(),
          replyTo = msgAdapters.inputDataProvider,
          filter = PbfFilter.DummyFilter
        )
        /* Ask for grid asset data */
        inputDataProvider ! ReqAssetTypes(
          runId = UUID.randomUUID(),
          replyTo = msgAdapters.inputDataProvider
        )

        /* Change state and await incoming data */
        awaitInputData(AwaitingData.empty(msgAdapters, replyTo), lvConfig)
      case (ctx, unsupported) =>
        ctx.log.error(
          s"Received unsupported message '$unsupported' in idle state."
        )
        stopBehavior
    }
    .receiveSignal { case (ctx, PostStop) =>
      postStopCleanUp(ctx.log)
    }

  /** State data to describe the actor's orientation while awaiting replies
    *
    * @param osmData
    *   Current state of information for open street maps data
    * @param assetInformation
    *   Current state of information for asset data
    * @param msgAdapters
    *   Collection of available message adapters
    * @param guardian
    *   Reference to the guardian actor
    */
  private final case class AwaitingData(
      osmData: Option[OsmoGridModel],
      assetInformation: Option[InputDataProvider.AssetInformation],
      msgAdapters: MessageAdapters,
      guardian: ActorRef[Response]
  ) {
    def registerResponse(
        response: InputDataProvider.Response,
        log: Logger
    ): Try[AwaitingData] = response match {
      case InputDataProvider.RepOsm(_, osmModel) =>
        log.debug(s"Received OSM data.")
        Success(copy(osmData = Some(osmModel)))
      case InputDataProvider.RepAssetTypes(assetInformation) =>
        log.debug(s"Received asset information.")
        Success(
          copy(assetInformation = Some(assetInformation))
        )
      /* Those states correspond to failed operation */
      case _: InputDataProvider.InvalidOsmRequest =>
        Failure(
          RequestFailedException(
            "The sent OSM data request was invalid. Stop generation."
          )
        )
      case InputDataProvider.OsmReadFailed(reason) =>
        Failure(
          RequestFailedException(
            "The requested OSM data cannot be read. Stop generation."
          )
        )
    }

  }

  private object AwaitingData {
    def empty(
        msgAdapters: MessageAdapters,
        guardian: ActorRef[Response]
    ): AwaitingData = AwaitingData(None, None, msgAdapters, guardian)
  }

  /** Await incoming input data and register it
    *
    * @param awaitingData
    *   State data for the awaiting
    * @param lvConfig
    *   Configuration for low voltage grid generation process
    * @return
    *   Equivalent next state
    */
  private def awaitInputData(
      awaitingData: AwaitingData,
      lvConfig: OsmoGridConfig.Generation.Lv
  ): Behavior[Request] = Behaviors
    .receive[Request] {
      case (ctx, MessageAdapters.WrappedInputDataResponse(response)) =>
        /* Register what has been responded */
        awaitingData.registerResponse(response, ctx.log) match {
          case Success(updatedStateData) =>
            handleUpdatedAwaitingData(awaitingData, lvConfig, ctx)
          case Failure(exception) =>
            ctx.log.error(
              "Request of needed input data failed. Stop low voltage grid generation.",
              exception
            )
            stopBehavior
        }
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
    * @param lvConfig
    *   Configuration for low voltage grid generation process
    * @return
    *   Next state
    */
  private def handleUpdatedAwaitingData(
      awaitingData: AwaitingData,
      lvConfig: OsmoGridConfig.Generation.Lv,
      ctx: ActorContext[Request]
  ): Behavior[Request] = {
    /* Check, if everything is in place */
    if (
      awaitingData.osmData.isDefined && awaitingData.assetInformation.isDefined
    ) {
      /* Process the data */
      ctx.log.debug("All awaited data is present. Start processing.")
      initRegionChunkDown(lvConfig, ctx)
      /* Wait for results to come up */
      awaitResults(awaitingData.guardian)
    } else
      awaitInputData(awaitingData, lvConfig) // Wait for missing data
  }

  /** Spawn child actors and start the chunking down of OSM data
    *
    * @param lvConfig
    *   Configuration for low voltage grid generation process
    * @param ctx
    *   [[ActorContext]] to spawn the children in
    */
  private def initRegionChunkDown(
      lvConfig: OsmoGridConfig.Generation.Lv,
      ctx: ActorContext[Request]
  ): Unit = {
    /* Spawn an coordinator for the region */
    val lvRegionCoordinator = ctx.spawnAnonymous(
      LvRegionCoordinator()
    )
    lvRegionCoordinator ! LvRegionCoordinator.Partition(
      lvConfig,
      ctx.messageAdapter(msg => MessageAdapters.WrappedRegionResponse(msg))
    )
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

        stopBehavior
      case (ctx, unsupported) =>
        ctx.log.error(
          s"Received an unsupported message: '$unsupported'. Shutting down."
        )
        stopBehavior
    }
    .receiveSignal { case (ctx, PostStop) =>
      postStopCleanUp(ctx.log)
    }

  /** Partial function to perform cleanup tasks while shutting down
    */
  override protected val cleanUp: () => Unit = ???
}
