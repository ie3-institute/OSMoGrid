/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.guardian.run

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.guardian.run.MessageAdapters
import edu.ie3.osmogrid.guardian.run.MessageAdapters.{
  WrappedListenerResponse,
  WrappedLvCoordinatorResponse
}
import edu.ie3.osmogrid.guardian.run.{RunSupport, StopSupport, SubGridHandling}
import edu.ie3.osmogrid.io.input.InputDataProvider
import edu.ie3.osmogrid.io.output.ResultListener
import edu.ie3.osmogrid.lv.LvCoordinator

import java.util.UUID
import scala.util.{Failure, Success}

/** Actor to take care of a specific simulation run
  */
object RunGuardian extends RunSupport with StopSupport with SubGridHandling {

  private final case class RunGuardianData(
      runId: UUID,
      cfg: OsmoGridConfig,
      additionalListener: Seq[ActorRef[ResultListener.ResultEvent]],
      msgAdapters: MessageAdapters
  )

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
            LvCoordinator.RepLvGrids(subGridContainers)
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
