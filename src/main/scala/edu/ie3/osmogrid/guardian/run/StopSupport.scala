/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.guardian.run

import akka.actor.typed.scaladsl.ActorContext
import edu.ie3.osmogrid.guardian.run.RunGuardian

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
      childReferences: ChildReferences,
      ctx: ActorContext[Request]
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
      watchMsg: Watch,
      stoppingData: StoppingData
  ): StoppingData = watchMsg match {
    case InputDataProviderDied =>
      stoppingData.copy(inputDataProviderTerminated = true)
    case ResultEventListenerDied =>
      stoppingData.copy(resultListenerTerminated = true)
    case LvCoordinatorDied =>
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
      watchMsg: Watch,
      ctx: ActorContext[Request]
  ): StoppingData = {
    (stopChildren(childReferences, ctx), watchMsg) match {
      case (stoppingData, InputDataProviderDied) =>
        ctx.log.warn(
          s"Input data provider for run $runId unexpectedly died. Start coordinated shut down phase for this run."
        )
        stoppingData.copy(inputDataProviderTerminated = true)
      case (stoppingData, ResultEventListenerDied) =>
        ctx.log.warn(
          s"One of the result listener for run $runId unexpectedly died. Start coordinated shut down phase for this run."
        )
        stoppingData.copy(resultListenerTerminated = true)
      case (stoppingData, LvCoordinatorDied) =>
        ctx.log.warn(
          s"Lv coordinator for run $runId unexpectedly died. Start coordinated shut down phase for this run."
        )
        stoppingData.copy(resultListenerTerminated = true)
    }

  }
}
