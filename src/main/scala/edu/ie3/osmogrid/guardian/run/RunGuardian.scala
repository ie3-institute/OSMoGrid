/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.guardian.run

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.guardian.run.RunGuardian.MessageAdapters.{WrappedListenerResponse, WrappedLvCoordinatorResponse}
import edu.ie3.osmogrid.guardian.run.{RunSupport, StopSupport, SubGridHandling}
import edu.ie3.osmogrid.io.input.InputDataProvider
import edu.ie3.osmogrid.io.output.ResultListener
import edu.ie3.osmogrid.lv.LvCoordinator

import java.util.UUID
import scala.util.{Failure, Success}

/** Actor to take care of a specific simulation run
  */
object RunGuardian extends RunSupport with StopSupport with SubGridHandling {
  sealed trait Request

  object Run extends Request

  /** Container object with all available adapters for outside protocol messages
    *
    * @param lvCoordinator
    *   Adapter for messages from [[LvCoordinator]]
    * @param resultListener
    *   Adapter for messages from [[ResultEventListener]]
    */
  private[run] final case class MessageAdapters(
      lvCoordinator: ActorRef[LvCoordinator.Response],
      resultListener: ActorRef[ResultListener.Response]
  )

  private[run] object MessageAdapters {
    final case class WrappedLvCoordinatorResponse(
        response: LvCoordinator.Response
    ) extends Request

    final case class WrappedListenerResponse(
        response: ResultListener.Response
    ) extends Request
  }

  sealed trait Response

  final case class Done(runId: UUID) extends Response

  sealed trait Watch extends Request

  private[run] object InputDataProviderDied extends Watch

  private[run] object ResultEventListenerDied extends Watch

  private[run] object LvCoordinatorDied extends Watch

  final case class RunGuardianData(
      runId: UUID,
      cfg: OsmoGridConfig,
      additionalListener: Seq[ActorRef[ResultListener.ResultEvent]],
      msgAdapters: MessageAdapters
  )

  private[run] final case class ChildReferences(
      inputDataProvider: ActorRef[InputDataProvider.Request],
      resultListener: Option[ActorRef[ResultListener.ResultEvent]],
      additionalResultListeners: Seq[ActorRef[ResultListener.ResultEvent]],
      lvCoordinator: Option[ActorRef[LvCoordinator.Request]]
  ) {
    def resultListeners: Seq[ActorRef[ResultListener.ResultEvent]] =
      resultListener
        .map(Seq(_))
        .getOrElse(Seq.empty) ++ additionalResultListeners
  }

  /** Meta data to keep track of which children already terminated during the
    * coordinated shutdown phase
    *
    * @param inputDataProviderTerminated
    *   If the [[InputDataProvider]] has stopped
    * @param resultListenerTerminated
    *   If the [[ResultListener]] has stopped
    * @param lvCoordinatorTerminated
    *   Optional information, if the [[LvCoordinator]] has stopped
    */
  private[run] final case class StoppingData(
      inputDataProviderTerminated: Boolean,
      resultListenerTerminated: Boolean,
      lvCoordinatorTerminated: Option[Boolean]
  ) {
    def allChildrenTerminated: Boolean =
      inputDataProviderTerminated && resultListenerTerminated && lvCoordinatorTerminated
        .contains(true)
  }

  /** Instantiate the actor
    *
    * @param cfg
    *   Configuration for the tool
    * @param additionalListener
    *   Addresses of additional listeners to be informed about results
    * @param runId
    *   Unique identifier for that generation run
    */
  def apply(
      cfg: OsmoGridConfig,
      additionalListener: Seq[ActorRef[ResultListener.ResultEvent]] = Seq.empty,
      runId: UUID
  ): Behavior[Request] = Behaviors.setup { ctx =>
    idle(
      runId,
      cfg,
      additionalListener,
      MessageAdapters(
        ctx.messageAdapter(msg => WrappedLvCoordinatorResponse(msg)),
        ctx.messageAdapter(msg => WrappedListenerResponse(msg))
      )
    )
  }

  /** This actor is in idle state and waits for any kind of request
    *
    * @param runId
    *   Identifier of the current run
    * @param cfg
    *   Configuration for this run
    * @param additionalListener
    *   References to additional [[ResultListener]]s
    * @param msgAdapters
    *   Collection of all message adapters
    * @return
    *   the next state
    */
  private[run] def idle(
      runId: UUID,
      cfg: OsmoGridConfig,
      additionalListener: Seq[ActorRef[ResultListener.ResultEvent]],
      msgAdapters: MessageAdapters
  ): Behavior[Request] =
    Behaviors.receive {
      case (ctx, Run) =>
        /* Start a run */
        initRun(runId, cfg, additionalListener, msgAdapters, ctx) match {
          case Success(childReferences) =>
            running(runId, cfg, childReferences, msgAdapters)
          case Failure(exception) =>
            ctx.log.error(s"Unable to start run $runId.", exception)
            Behaviors.stopped
        }
      case (ctx, notUnderstood) =>
        ctx.log.error(
          s"Received a message, that I don't understand during idle phase of run $runId.\n\tMessage: $notUnderstood"
        )
        Behaviors.same
    }

  /** Behavior to indicate, that a simulation run is currently active
    *
    * @param runId
    *   Identifier of the run
    * @param cfg
    *   Configuration for this run
    * @param childReferences
    *   References to child actors
    * @param msgAdapters
    *   Collection of message adapters
    * @return
    *   The next state
    */
  private def running(
      runId: UUID,
      cfg: OsmoGridConfig,
      childReferences: ChildReferences,
      msgAdapters: MessageAdapters
  ): Behavior[Request] = Behaviors.receive {
    case (
          ctx,
          WrappedLvCoordinatorResponse(
            LvCoordinator.RepLvGrids(_, subGridContainers)
          )
        ) =>
      /* Handle the grid results and wait for the listener to report back */
      handleLvResults(
        subGridContainers,
        cfg.generation,
        childReferences.resultListeners,
        msgAdapters
      )(ctx.log)
      Behaviors.same
    case (
          ctx,
          WrappedListenerResponse(ResultListener.ResultHandled(_, sender))
        ) =>
      ctx.log.debug(
        s"The listener $sender has successfully handled the result event of run $runId."
      )
      if (
        childReferences.resultListener.contains(
          sender
        ) || childReferences.resultListener.isEmpty
      ) {
        /* Start coordinated shutdown */
        ctx.log.info(
          s"Run $runId successfully finished. Stop all run-related processes."
        )
        stopping(runId, stopChildren(childReferences, ctx))
      } else {
        /* Somebody did something great, but nothing, that affects us */
        Behaviors.same
      }
    case (ctx, watch: Watch) =>
      /* Somebody died unexpectedly. Start coordinated shutdown */
      stopping(
        runId,
        handleUnexpectedShutDown(runId, childReferences, watch, ctx)
      )
    case (ctx, notUnderstood) =>
      ctx.log.error(
        s"Received a message, that I don't understand during active run $runId.\n\tMessage: $notUnderstood"
      )
      Behaviors.same
  }

  /** Behavior, that indicates, that a coordinate shutdown of the children takes
    * place
    *
    * @param runId
    *   Identifier of the run
    * @param stoppingData
    *   Information about who already has terminated
    * @return
    *   The next state
    */
  private def stopping(
      runId: UUID,
      stoppingData: StoppingData
  ): Behavior[Request] = Behaviors.receive {
    case (ctx, watch: Watch) =>
      val updatedStoppingData = registerCoordinatedShutDown(watch, stoppingData)
      if (updatedStoppingData.allChildrenTerminated) {
        ctx.log.info(
          s"All child processes of run $runId successfully terminated. Finally terminating the whole run process."
        )
        /* The overall guardian is automatically informed via death watch */
        Behaviors.stopped
      } else stopping(runId, updatedStoppingData)
    case (ctx, notUnderstood) =>
      ctx.log.error(
        s"Received a message, that I don't understand during coordinated shutdown phase of run $runId.\n\tMessage: $notUnderstood"
      )
      Behaviors.same
  }
}
