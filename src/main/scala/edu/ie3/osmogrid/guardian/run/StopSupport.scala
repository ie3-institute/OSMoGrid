/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */
package edu.ie3.osmogrid.guardian.run

import org.apache.pekko.actor.typed.scaladsl.ActorContext
import edu.ie3.osmogrid.io.input.InputTerminate
import edu.ie3.osmogrid.lv.LvTerminate
import edu.ie3.osmogrid.mv.MvTerminate

import java.util.UUID

trait StopSupport {

  /** Stop all children for the given run. The additional listeners are not
    * asked to be stopped!
    *
    * @param runId
    *   Identifier of the current run
    * @param childReferences
    *   References to children
    * @param ctx
    *   Current actor context
    */
  protected def stopChildren(
      runId: UUID,
      childReferences: ChildReferences,
      ctx: ActorContext[RunRequest]
  ): StoppingData = {
    childReferences.lvCoordinator.foreach(_ ! LvTerminate)
    childReferences.mvCoordinator.foreach(_ ! MvTerminate)
    childReferences.inputDataProvider ! InputTerminate

    StoppingData(
      runId,
      inputDataProviderTerminated = false,
      resultListenerTerminated = false,
      childReferences.lvCoordinator.map(_ => false),
      childReferences.mvCoordinator.map(_ => false)
    )
  }

  /** Register [[RunWatch]] messages within the coordinated shutdown phase of a
    * run
    *
    * @param watchMsg
    *   Received [[RunWatch]] message
    * @param stoppingData
    *   State data for the stopping run
    * @return
    *   Next state with updated [[StoppingData]]
    */
  protected def registerCoordinatedShutDown(
      watchMsg: RunWatch,
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
    case MvCoordinatorDied =>
      stoppingData.copy(mvCoordinatorTerminated =
        stoppingData.mvCoordinatorTerminated.map(_ => true)
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
    *   Received [[RunWatch]] message
    * @param ctx
    *   Current Actor context
    * @return
    *   Next state with updated [[StoppingData]]
    */
  protected def handleUnexpectedShutDown(
      runId: UUID,
      childReferences: ChildReferences,
      watchMsg: RunWatch,
      ctx: ActorContext[RunRequest]
  ): StoppingData = {
    (stopChildren(runId, childReferences, ctx), watchMsg) match {
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
        stoppingData.copy(lvCoordinatorTerminated =
          stoppingData.lvCoordinatorTerminated.map(_ => true)
        )
      case (stoppingData, MvCoordinatorDied) =>
        ctx.log.warn(
          s"Mv coordinator for run $runId unexpectedly died. Start coordinated shut down phase for this run."
        )
        stoppingData.copy(mvCoordinatorTerminated =
          stoppingData.mvCoordinatorTerminated.map(_ => true)
        )
    }

  }
}
