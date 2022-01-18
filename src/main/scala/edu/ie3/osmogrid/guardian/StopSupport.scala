/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.guardian

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.ActorContext
import edu.ie3.osmogrid.guardian.RunGuardian.{ChildReferences, StoppingData}
import org.slf4j.Logger

import java.util.UUID

trait StopSupport {

  /** Stop all children for the given run. The additional listeners are not
    * asked to be stopped!
    *
    * @param childReferences
    *   References to children
    * @param ctx
    *   Current actor context
    */
  protected def stopChildren(
      childReferences: RunGuardian.ChildReferences,
      ctx: ActorContext[RunGuardian.Request]
  ): StoppingData = {
    ctx.stop(childReferences.inputDataProvider)
    childReferences.resultListener.foreach(ctx.stop)

    StoppingData(false, false, childReferences.lvCoordinator.map(_ => false))
  }

  /** Register [[Watch]] messages within the coordinated shutdown phase of a run
    *
    * @param watchMsg
    *   Received [[Watch]] message
    * @param stoppingData
    *   State data for the stopping run
    * @return
    *   Next state with updated [[GuardianData]]
    */
  protected def registerCoordinatedShutDown(
      watchMsg: RunGuardian.Watch,
      stoppingData: StoppingData
  ): StoppingData = watchMsg match {
    case RunGuardian.InputDataProviderDied =>
      stoppingData.copy(inputDataProviderTerminated = true)
    case RunGuardian.ResultEventListenerDied =>
      stoppingData.copy(resultListenerTerminated = true)
    case RunGuardian.LvCoordinatorDied =>
      stoppingData.copy(lvCoordinatorTerminated =
        stoppingData.lvCoordinatorTerminated.map(_ => true)
      )
  }

  /** Handle an unexpected shutdown of children and start coordinated shutdown
    * phase for that run
    *
    * @param runId
    *   Identifier of the current run
    * @param childReferences
    *   References to child actors
    * @param watchMsg
    *   Received [[Watch]] message
    * @param ctx
    *   Current Actor context
    * @return
    *   Next state with updated [[GuardianData]]
    */
  protected def handleUnexpectedShutDown(
      runId: UUID,
      childReferences: ChildReferences,
      watchMsg: RunGuardian.Watch,
      ctx: ActorContext[RunGuardian.Request]
  ): StoppingData = {
    (stopChildren(childReferences, ctx), watchMsg) match {
      case (stoppingData, RunGuardian.InputDataProviderDied) =>
        ctx.log.warn(
          s"Input data provider for run $runId unexpectedly died. Start coordinated shut down phase for this run."
        )
        stoppingData.copy(inputDataProviderTerminated = true)
      case (stoppingData, RunGuardian.ResultEventListenerDied) =>
        ctx.log.warn(
          s"One of the result listener for run $runId unexpectedly died. Start coordinated shut down phase for this run."
        )
        stoppingData.copy(resultListenerTerminated = true)
      case (stoppingData, RunGuardian.LvCoordinatorDied) =>
        ctx.log.warn(
          s"Lv coordinator for run $runId unexpectedly died. Start coordinated shut down phase for this run."
        )
        stoppingData.copy(resultListenerTerminated = true)
    }

  }
}
