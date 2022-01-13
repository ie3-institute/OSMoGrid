/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.guardian

import akka.actor.Terminated
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorRef
import edu.ie3.datamodel.models.input.container.{
  JointGridContainer,
  SubGridContainer
}
import edu.ie3.datamodel.utils.ContainerUtils
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.cfg.OsmoGridConfig.Generation
import edu.ie3.osmogrid.io.input.InputDataProvider
import edu.ie3.osmogrid.io.input.InputDataProvider.Request
import edu.ie3.osmogrid.io.output.ResultListener
import edu.ie3.osmogrid.io.output.ResultListener.{GridResult, ResultEvent}
import edu.ie3.osmogrid.lv.LvCoordinator
import edu.ie3.osmogrid.lv.LvCoordinator.{RepLvGrids, ReqLvGrids}

import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Success, Try}

object OsmoGridGuardian {

  /* Messages, that are understood and sent */
  sealed trait Request
  final case class Run(cfg: OsmoGridConfig) extends Request
  object InputDataProviderDied extends Request
  object ResultEventListenerDied extends Request
  object LvCoordinatorDied extends Request

  /* Container class for message adapters as well as wrapping classes themselves */
  private final case class MessageAdapters(
      lvCoordinator: ActorRef[LvCoordinator.Response]
  )
  private object MessageAdapters {
    final case class WrappedLvCoordinatorResponse(
        response: LvCoordinator.Response
    ) extends Request
  }

  def apply(): Behavior[Request] = Behaviors.setup { context =>
    /* Define message adapters */
    val messageAdapters =
      MessageAdapters(
        context.messageAdapter(msg =>
          MessageAdapters.WrappedLvCoordinatorResponse(msg)
        )
      )

    idle(messageAdapters)
  }

  private def idle(msgAdapters: MessageAdapters): Behavior[Request] =
    Behaviors.receive {
      case (ctx, Run(cfg)) =>
        ctx.log.info("Initializing grid generation!")

        ctx.log.info("Starting input data provider")
        val inputProvider =
          ctx.spawn(InputDataProvider(cfg.input), "InputDataProvider")
        ctx.watchWith(inputProvider, InputDataProviderDied)
        ctx.log.debug("Starting output data listener")
        val resultEventListener =
          ctx.spawn(ResultListener(cfg.output), "ResultListener")
        ctx.watchWith(resultEventListener, ResultEventListenerDied)

        /* Check, which voltage level configs are given. Start with lv level, if this is desired for. */
        cfg.generation match {
          case Generation(Some(lvConfig)) =>
            ctx.log.debug("Starting low voltage grid coordinator.")
            val lvCoordinator = ctx.spawn(LvCoordinator(), "LvCoordinator")
            ctx.watchWith(lvCoordinator, LvCoordinatorDied)
            lvCoordinator ! ReqLvGrids(lvConfig, msgAdapters.lvCoordinator)
            awaitLvGrids(inputProvider, resultEventListener)
          case unsupported =>
            ctx.log.error(
              "Received unsupported grid generation config. Bye, bye."
            )
            Behaviors.stopped
        }
      case (ctx, unsupported) =>
        ctx.log.error(s"Received unsupported message '$unsupported'.")
        Behaviors.stopped
    }

  private def awaitLvGrids(
      inputDataProvider: ActorRef[InputDataProvider.Request],
      resultListener: ActorRef[ResultListener.ResultEvent]
  ): Behaviors.Receive[Request] =
    Behaviors.receive {
      case (
            ctx,
            MessageAdapters.WrappedLvCoordinatorResponse(RepLvGrids(lvGrids))
          ) =>
        ctx.log.info(s"Received ${lvGrids.length} lv grids. Join them.")
        Try(ContainerUtils.combineToJointGrid(lvGrids.asJava)) match {
          case Success(jointGrid) =>
            resultListener ! GridResult(jointGrid, ctx.self)
            awaitShutDown(inputDataProvider)
          case Failure(exception) =>
            ctx.log.error(
              "Combination of received sub-grids failed. Shutting down."
            )
            Behaviors.stopped
        }
      case (ctx, unsupported) =>
        ctx.log.error(
          s"Received unsupported message while waiting for lv grids. Unsupported: $unsupported"
        )
        Behaviors.stopped
    }

  private def awaitShutDown(
      inputDataProvider: ActorRef[InputDataProvider.Request],
      resultListenerTerminated: Boolean = false,
      inputDataProviderTerminated: Boolean = false
  ): Behaviors.Receive[Request] = Behaviors.receive { (ctx, msg) =>
    msg match {
      case ResultEventListenerDied =>
        ctx.log.info("Result listener finished handling the result.")
        ctx.log.debug("Shut down input data provider.")
        awaitShutDown(inputDataProvider, resultListenerTerminated = true)
      case InputDataProviderDied if resultListenerTerminated =>
        /* That's the fine case */
        ctx.log.info("Input data provider shut down.")
        Behaviors.stopped
      case InputDataProviderDied =>
        /* That's the malicious case */
        ctx.log.error(
          "Input data provider unexpectedly died during shutdown was initiated."
        )
        Behaviors.stopped
      case unsupported =>
        ctx.log.error(s"Received an unsupported message $unsupported.")
        Behaviors.stopped
    }
  }
}
