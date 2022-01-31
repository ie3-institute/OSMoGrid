/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import akka.actor.typed.{ActorRef, Behavior, PostStop, SupervisorStrategy}
import akka.actor.typed.scaladsl.{Behaviors, Routers}
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.cfg.OsmoGridConfig.Generation.Lv
import edu.ie3.osmogrid.io.input.InputDataProvider
import edu.ie3.osmogrid.lv.LvCoordinator.cleanUp
import edu.ie3.osmogrid.lv.LvGridGenerator

import java.util.UUID

object LvCoordinator {
  sealed trait Request
  final case class ReqLvGrids(
      cfg: OsmoGridConfig.Generation.Lv,
      replyTo: ActorRef[Response]
  ) extends Request

  object Terminate extends Request
  /* Container class for message adapters as well as wrapping classes themselves */
  private final case class MessageAdapters(
      inputDataProvider: ActorRef[InputDataProvider.Response],
      regionCoordinator: ActorRef[LvRegionCoordinator.Response],
      gridGenerator: ActorRef[LvGridGenerator.Response]
  )
  private object MessageAdapters {
    final case class WrappedInputDataResponse(
        response: InputDataProvider.Response
    ) extends Request

    final case class WrappedRegionResponse(
        response: LvRegionCoordinator.Response
    ) extends Request
    final case class WrappedGridResponse(response: LvGridGenerator.Response)
        extends Request
  }

  sealed trait Response
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
        ),
        context.messageAdapter(msg => MessageAdapters.WrappedGridResponse(msg))
      )

    idle(messageAdapters)
  }

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

        awaitInputData(osmData = None, assetData = None, replyTo)
      case (ctx, unsupported) =>
        ctx.log.error(s"Received unsupported message: $unsupported")
        Behaviors.stopped(cleanUp())
    }
    .receiveSignal { case (ctx, PostStop) =>
      ctx.log.info("Got terminated by ActorSystem.")
      cleanUp()()
      Behaviors.same
    }

  /** Await incoming input data and register it
    *
    * TODO: Adapt accordingly to have classes that collect the data
    *
    * @param osmData
    *   Osm data to await
    * @param assetData
    *   Asset data to await
    * @param guardian
    *   Reference to the guardian
    * @return
    *   Equivalent next state
    */
  private def awaitInputData(
      osmData: Option[Int] = None,
      assetData: Option[Int] = None,
      guardian: ActorRef[Response]
  ): Behavior[Request] = Behaviors.receive { case (ctx, msg) =>
    /* TODO: Register data according to message content */

    /* Check, if everything is in place */
    if (osmData.isDefined && assetData.isDefined) {
      /* Process the data */
      ctx.log.debug("All awaited data is present. Start processing.")

      /* Spawn an coordinator for the region */
      val lvRegionCoordinator = ctx.spawn(
        LvRegionCoordinator(),
        "LvRegionCoordinator"
      ) // TODO: Add run id to name
      lvRegionCoordinator ! LvRegionCoordinator.Partition(
        ctx.messageAdapter(msg => MessageAdapters.WrappedRegionResponse(msg))
      )

      /* Wait in idle for everything to come up */
      awaitResults(Vector.empty[SubGridContainer], guardian)
    } else
      Behaviors.same // Wait for missing data
  }

  private def awaitResults(
      subGrids: Seq[SubGridContainer],
      guardian: ActorRef[Response]
  ): Behavior[Request] = Behaviors.receive {
    case (
          ctx,
          MessageAdapters.WrappedRegionResponse(LvRegionCoordinator.Done)
        ) =>
      ctx.log.info(
        s"Low voltage grid generation succeeded."
      )
      val finalizedSubGrids = finalize(subGrids)

      /* Report back the collected grids */
      guardian ! RepLvGrids(finalizedSubGrids)

      Behaviors.stopped
    case (ctx, unsupported) =>
      ctx.log.error(
        s"Received an unsupported message: '$unsupported'. Shutting down."
      )
      Behaviors.stopped
  }

  /** Finalize the collection of received grids
    *
    * @param subGrids
    *   Yet collected sub grids
    * @return
    *   Next state
    */
  private def finalize(
      subGrids: Seq[SubGridContainer]
  ): Seq[SubGridContainer] = ???

  private def cleanUp(): () => Unit = ???
}
