/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.guardian

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import edu.ie3.osmogrid.guardian.OsmoGridGuardian.RunData.Stopping
import edu.ie3.osmogrid.guardian.OsmoGridGuardian.{
  GuardianData,
  GuardianWatch,
  InputDataProviderDied,
  LvCoordinatorDied,
  Request,
  ResultEventListenerDied,
  RunData,
  idle
}
import org.slf4j.Logger

import java.util.UUID

trait StopSupport {

  protected def stopRunProcesses(
      guardianData: GuardianData,
      runId: UUID,
      ctx: ActorContext[Request]
  ): GuardianData = guardianData.runs
    .get(runId)
    .map {
      case running: RunData.Running =>
        val stoppingRun = stopChildrenByRun(running, ctx)
        guardianData.replace(stoppingRun)
      case stopping: RunData.Stopping if !stopping.successfullyTerminated =>
        ctx.log
          .debug(s"Children for run $runId already scheduled for stopping.")
        guardianData
      case stopping: RunData.Stopping =>
        ctx.log.warn(s"Children for run $runId already successfully stopped.")
        guardianData
    }
    .getOrElse {
      ctx.log.debug(s"Cannot stop run children. No run with id $runId found.")
      guardianData
    }

  /** Stop all children for the given run
    *
    * @param runData
    *   Current run meta data
    * @param ctx
    *   Current actor context
    */
  private def stopChildrenByRun(
      runData: RunData.Running,
      ctx: ActorContext[Request]
  ): RunData.Stopping = {
    ctx.stop(runData.inputDataProvider)
    runData.resultEventListener.foreach(ctx.stop)
    runData.toStopping
  }

  /** Handle a [[GuardianWatch]] message and act accordingly. Either register
    * successful shutdown of children in coordinated shutdown phase or initiate
    * it, if somebody died unexpectedly.
    *
    * @param watchMsg
    *   Received [[GuardianWatch]] message
    * @param guardianData
    *   Current [[GuardianData]]
    * @param ctx
    *   Current Actor context
    * @return
    *   Next state with updated [[GuardianData]]
    */
  protected def handleGuardianWatchEvent(
      watchMsg: GuardianWatch,
      guardianData: GuardianData,
      ctx: ActorContext[Request]
  ): Behavior[Request] = {
    implicit val log: Logger = ctx.log
    guardianData.runs.get(watchMsg.runId) match {
      case Some(stopping: RunData.Stopping) =>
        /* This run is scheduled for stopping. Register the replies. */
        registerCoordinatedShutDown(watchMsg, stopping, guardianData)
      case Some(running: RunData.Running) =>
        /* This run is NOT scheduled for shutdown. Start coordinated shut down phase. */
        handleUnexpectedShutDown(watchMsg, guardianData, ctx)
      case None =>
        ctx.log.warn(
          s"Received a watch message '$watchMsg' for a run, that is not known to the guardian. Just keep on going..."
        )
        Behaviors.same
    }
  }

  /** Register [[GuardianWatch]] messages within the coordinated shutdown phase
    * of a run
    *
    * @param watchMsg
    *   Received [[GuardianWatch]] message
    * @param stopping
    *   State data for the stopping run
    * @param guardianData
    *   Current [[GuardianData]]
    * @param log
    *   Logger
    * @return
    *   Next state with updated [[GuardianData]]
    */
  private def registerCoordinatedShutDown(
      watchMsg: GuardianWatch,
      stopping: RunData.Stopping,
      guardianData: GuardianData
  )(implicit log: Logger): Behavior[Request] = {
    val updatedGuardianData = watchMsg match {
      case InputDataProviderDied(_) =>
        updateGuardianData(
          stopping.copy(inputDataProviderTerminated = true),
          guardianData
        )
      case ResultEventListenerDied(runId) =>
        updateGuardianData(
          stopping.copy(resultListenerTerminated = true),
          guardianData
        )
      case LvCoordinatorDied(runId) =>
        log.debug(
          s"LV coordinator died in coordinated shutdown phase for run $runId."
        )
        guardianData
    }
    idle(updatedGuardianData)
  }

  /** Update the guardian data by removing or updating the meta information
    * about the given run
    *
    * @param runData
    *   Most recent run meta information
    * @param guardianData
    *   Data to update
    * @param log
    *   Logger
    * @return
    *   The updated [[GuardianData]]
    */
  private def updateGuardianData(
      runData: Stopping,
      guardianData: GuardianData
  )(implicit log: Logger): GuardianData = if (runData.successfullyTerminated) {
    log.debug(s"Run with id ${runData.runId} successfully terminated.")
    guardianData.remove(runData.runId)
  } else guardianData.replace(runData)

  /** Handle an unexpected shutdown of children and start coordinated shutdown
    * phase for that run
    *
    * @param watchMsg
    *   Received [[GuardianWatch]] message
    * @param guardianData
    *   Current [[GuardianData]]
    * @param ctx
    *   Current Actor context
    * @return
    *   Next state with updated [[GuardianData]]
    */
  private def handleUnexpectedShutDown(
      watchMsg: GuardianWatch,
      guardianData: GuardianData,
      ctx: ActorContext[Request]
  ) = {
    watchMsg match {
      case InputDataProviderDied(runId) =>
        ctx.log.warn(
          s"Input data provider for run $runId unexpectedly died. Start coordinated shut down phase for this run."
        )
      case ResultEventListenerDied(runId) =>
        ctx.log.warn(
          s"One of the result listener for run $runId unexpectedly died. Start coordinated shut down phase for this run."
        )
      case LvCoordinatorDied(runId) =>
        ctx.log.warn(
          s"Lv coordinator for run $runId unexpectedly died. Start coordinated shut down phase for this run."
        )
    }
    idle(stopRunProcesses(guardianData, watchMsg.runId, ctx))
  }
}
