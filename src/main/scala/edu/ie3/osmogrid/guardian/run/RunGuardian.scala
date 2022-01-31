/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.guardian.run

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.guardian.run.MessageAdapters
import edu.ie3.osmogrid.guardian.run.MessageAdapters.WrappedLvCoordinatorResponse
import edu.ie3.osmogrid.guardian.run.{RunSupport, StopSupport, SubGridHandling}
import edu.ie3.osmogrid.io.input.InputDataProvider
import edu.ie3.osmogrid.io.output.{ResultListener, ResultListenerProtocol}
import edu.ie3.osmogrid.lv.LvCoordinator

import java.util.UUID
import scala.util.{Failure, Success}

/** Actor to take care of a specific simulation run
  */
object RunGuardian extends RunSupport with StopSupport with SubGridHandling {

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
      additionalListener: Seq[ActorRef[ResultListenerProtocol.Request]] =
        Seq.empty,
      runId: UUID
  ): Behavior[Request] = Behaviors.setup { ctx =>
    idle(
      RunGuardianData(
        runId,
        cfg,
        additionalListener,
        MessageAdapters(
          ctx.messageAdapter(msg => WrappedLvCoordinatorResponse(msg))
        )
      )
    )
  }

  /** This actor is in idle state and waits for any kind of request
    *
    * @param runGuardianData
    *   Meta information describing the current actor's state
    * @return
    *   the next state
    */
  private def idle(runGuardianData: RunGuardianData): Behavior[Request] =
    Behaviors.receive {
      case (ctx, Run) =>
        /* Start a run */
        initRun(
          runGuardianData,
          ctx
        ) match {
          case Success(childReferences) =>
            running(
              runGuardianData,
              childReferences
            )
          case Failure(exception) =>
            ctx.log.error(
              s"Unable to start run ${runGuardianData.runId}.",
              exception
            )
            Behaviors.stopped
        }
      case (ctx, notUnderstood) =>
        ctx.log.error(
          s"Received a message, that I don't understand during idle phase of run ${runGuardianData.runId}.\n\tMessage: $notUnderstood"
        )
        Behaviors.same
    }

  /** Behavior to indicate, that a simulation run is currently active
    *
    * @param runGuardianData
    *   Meta information describing the current actor's state
    * @param childReferences
    *   References to child actors
    * @return
    *   The next state
    */
  private def running(
      runGuardianData: RunGuardianData,
      childReferences: ChildReferences
  ): Behavior[Request] = Behaviors.receive {
    case (
          ctx,
          WrappedLvCoordinatorResponse(
            LvCoordinator.RepLvGrids(subGridContainers)
          )
        ) =>
      /* Handle the grid results and wait for the listener to report back */
      val resultingGrid = handleLvResults(
        subGridContainers,
        runGuardianData.cfg.generation
      )(ctx.log)

      // spin up listeners, watch them and wait until they terminate in this state
      spawnResultListener(
        runGuardianData.runId,
        runGuardianData.cfg.output,
        ctx
      ).foreach(_ ! ResultListenerProtocol.GridResult(resultingGrid))
      Behaviors.same

    case (ctx, ResultEventListenerDied) =>
      // we wait for exact one listener as we only started one
      /* Start coordinated shutdown */
      ctx.log.info(
        s"Run ${runGuardianData.runId} finished. Stop all run-related processes."
      )
      stopping(stopChildren(runGuardianData.runId, childReferences, ctx))
    case (ctx, watch: Watch) =>
      /* Somebody died unexpectedly. Start coordinated shutdown */
      stopping(
        handleUnexpectedShutDown(
          runGuardianData.runId,
          childReferences,
          watch,
          ctx
        )
      )
    case (ctx, notUnderstood) =>
      ctx.log.error(
        s"Received a message, that I don't understand during active run ${runGuardianData.runId}.\n\tMessage: $notUnderstood"
      )
      Behaviors.same
  }

  /** Behavior, that indicates, that a coordinate shutdown of the children takes
    * place
    *
    * @param stoppingData
    *   Information about who already has terminated
    * @return
    *   The next state
    */
  private def stopping(
      stoppingData: StoppingData
  ): Behavior[Request] = Behaviors.receive {
    case (ctx, watch: Watch) =>
      val updatedStoppingData = registerCoordinatedShutDown(watch, stoppingData)
      if (updatedStoppingData.allChildrenTerminated) {
        ctx.log.info(
          s"All child processes of run ${stoppingData.runId} successfully terminated. Finally terminating the whole run process."
        )
        /* The overall guardian is automatically informed via death watch */
        Behaviors.stopped
      } else stopping(updatedStoppingData)
    case (ctx, notUnderstood) =>
      ctx.log.error(
        s"Received a message, that I don't understand during coordinated shutdown phase of run ${stoppingData.runId}.\n\tMessage: $notUnderstood"
      )
      Behaviors.same
  }
}
