/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.guardian

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
import edu.ie3.osmogrid.io.output.ResultListener
import edu.ie3.osmogrid.io.output.ResultListener.{GridResult, Request}
import edu.ie3.osmogrid.lv.LvCoordinator
import edu.ie3.osmogrid.lv.LvCoordinator.ReqLvGrids

import java.util.UUID
import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Success, Try}

object OsmoGridGuardian {

  /* Messages, that are understood and sent */
  sealed trait Request
  final case class Run(
      cfg: OsmoGridConfig,
      additionalListener: Vector[ActorRef[ResultListener.ResultEvent]] =
        Vector.empty,
      runId: UUID = UUID.randomUUID()
  ) extends Request
  @Deprecated("Use child classes of GuardianWatch")
  object InputDataProviderDied extends Request
  @Deprecated("Use child classes of GuardianWatch")
  object ResultEventListenerDied extends Request
  @Deprecated("Use child classes of GuardianWatch")
  object LvCoordinatorDied extends Request

  sealed trait Response
  final case class RepLvGrids(runId: UUID, grids: Seq[SubGridContainer])
      extends Response

  /* dead watch events */
  sealed trait GuardianWatch extends Request {
    val runId: UUID
  }

  private final case class InputDataProviderDied(runId: UUID)
      extends GuardianWatch

  private final case class ResultEventListenerDied(runId: UUID)
      extends GuardianWatch

  private final case class LvCoordinatorDied(runId: UUID) extends GuardianWatch

  /** Relevant, state-independent data, the the actor needs to know
    *
    * @param msgAdapters
    *   Collection of all message adapters
    * @param runs
    *   Currently active conversion runs
    * @param persistenceResponseMapper
    *   Adapter for messages from [[ResultListener]]
    */
  private final case class GuardianData(
      msgAdapters: MessageAdapters,
      runs: Map[UUID, RunData],
      @Deprecated("Use the entry in msgAdapters")
      persistenceResponseMapper: ActorRef[ResultListener.Response]
  ) {

    def append(run: RunData): GuardianData =
      this.copy(runs = runs + (run.runId -> run))

    def remove(runId: UUID): GuardianData =
      this.copy(runs = runs - runId)
  }

  /** Container object with all available adapters for outside protocol messages
    *
    * @param lvCoordinator
    *   Adapter for messages from [[LvCoordinator]]
    * @param resultListener
    *   Adapter for messages from [[ResultEventListener]]
    */
  private final case class MessageAdapters(
      lvCoordinator: ActorRef[LvCoordinator.Response],
      resultListener: ActorRef[ResultListener.Response]
  )
  private object MessageAdapters {
    final case class WrappedLvCoordinatorResponse(
        response: LvCoordinator.Response
    ) extends Request

    final case class WrappedListenerResponse(
        response: ResultListener.Response
    ) extends Request
  }

  /** Meta data regarding a certain given generation run
    *
    * @param runId
    *   Identifier of the run
    * @param cfg
    *   Configuration for that given run
    * @param resultEventListener
    *   Listeners to inform about results
    * @param inputDataProvider
    *   Reference to the input data provider
    */
  private final case class RunData(
      runId: UUID,
      cfg: OsmoGridConfig,
      resultEventListener: Vector[ActorRef[ResultListener.ResultEvent]],
      inputDataProvider: ActorRef[InputDataProvider.Request]
  )
  private case object RunData {
    def apply(
        run: Run,
        resultEventListener: Vector[ActorRef[ResultListener.ResultEvent]],
        inputDataProvider: ActorRef[InputDataProvider.Request]
    ): RunData =
      RunData(
        run.runId,
        run.cfg,
        run.additionalListener ++ resultEventListener,
        inputDataProvider
      )
  }

  def apply(): Behavior[Request] = Behaviors.setup { context =>
    /* Define and register message adapters */
    val messageAdapters =
      MessageAdapters(
        context.messageAdapter(msg =>
          MessageAdapters.WrappedLvCoordinatorResponse(msg)
        ),
        context.messageAdapter(msg =>
          MessageAdapters.WrappedListenerResponse(msg)
        )
      )

    idle(messageAdapters)
  }

  private def idle(msgAdapters: MessageAdapters): Behavior[Request] =
    Behaviors.receive {
      case (ctx, Run(cfg, additionalListener, runId)) =>
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
      resultListener: ActorRef[ResultListener.Request]
  ): Behaviors.Receive[Request] =
    Behaviors.receive {
      case (
            ctx,
            MessageAdapters.WrappedLvCoordinatorResponse(
              LvCoordinator.RepLvGrids(lvGrids)
            )
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
