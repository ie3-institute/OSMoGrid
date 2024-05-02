/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.guardian.run

import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.guardian.run.MessageAdapters.{
  WrappedLvCoordinatorResponse,
  WrappedMvCoordinatorResponse
}
import edu.ie3.osmogrid.io.output.ResultListenerProtocol
import edu.ie3.osmogrid.lv.RepLvGrids
import edu.ie3.osmogrid.mv.{ProvidedLvData, RepMvGrids, WrappedMvResponse}

import java.util.UUID
import scala.util.{Failure, Success}

/** Actor to take care of a specific simulation run
  */
object RunGuardian
    extends RunSupport
    with StopSupport
    with SubGridHandling
    with VoltageSupport {

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
      additionalListener: Seq[ActorRef[ResultListenerProtocol]] = Seq.empty,
      runId: UUID
  ): Behavior[RunRequest] = Behaviors.setup { ctx =>
    // overwriting the default voltage config
    setVoltageConfig(cfg.voltage)

    idle(
      RunGuardianData(
        runId,
        cfg,
        additionalListener,
        MessageAdapters(
          ctx.messageAdapter(msg => WrappedLvCoordinatorResponse(msg)),
          ctx.messageAdapter(msg => WrappedMvCoordinatorResponse(msg))
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
  private def idle(runGuardianData: RunGuardianData): Behavior[RunRequest] =
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
              childReferences,
              FinishedGridData.empty
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
    * @param finishedGridData
    *   Container for finished grid data
    * @return
    *   The next state
    */
  private def running(
      runGuardianData: RunGuardianData,
      childReferences: ChildReferences,
      finishedGridData: FinishedGridData
  ): Behavior[RunRequest] = Behaviors.receive {
    case (
          ctx,
          WrappedLvCoordinatorResponse(
            RepLvGrids(subGridContainer, streetGraph)
          )
        ) =>
      ctx.log.info(s"Received lv grids.")

      // lv coordinator is now allowed to die in peace
      childReferences.lvCoordinator.foreach(ctx.unwatch)
      val updatedChildReferences = childReferences.copy(lvCoordinator = None)

      // if a mv coordinator is present, send the lv results to the mv coordinator
      childReferences.mvCoordinator.foreach { mv =>
        mv ! WrappedMvResponse(
          ProvidedLvData(subGridContainer, streetGraph)
        )
      }

      val updated = finishedGridData.copy(lvData = Some(subGridContainer))

      // check if all possible data was received
      if (!updatedChildReferences.stillRunning) {

        // if all data was received,
        ctx.self ! HandleGridResults
        running(runGuardianData, updatedChildReferences, updated)
      } else {

        // if some expected data is still missing, keep waiting for missing data
        running(runGuardianData, updatedChildReferences, updated)
      }

    case (
          ctx,
          WrappedMvCoordinatorResponse(
            RepMvGrids(
              subGridContainer,
              dummyHvGrid,
              nodeChanges,
              assetInformation
            )
          )
        ) =>
      ctx.log.info(s"Received mv grids.")

      // mv coordinator is now allowed to die in peace
      childReferences.mvCoordinator.foreach(ctx.unwatch)
      val updatedChildReferences = childReferences.copy(mvCoordinator = None)

      val updated = finishedGridData.copy(
        mvData = Some(subGridContainer),
        hvData = dummyHvGrid.map(Seq(_)), // converting to sequence
        mvNodeChanges = Option.when(nodeChanges.nonEmpty)(nodeChanges),
        assetInformation = Some(assetInformation)
      )

      // check if all possible data was received
      if (!updatedChildReferences.stillRunning) {

        // if all data was received,
        ctx.self ! HandleGridResults
        running(runGuardianData, updatedChildReferences, updated)
      } else {

        // if some expected data is still missing, keep waiting for missing data
        running(runGuardianData, updatedChildReferences, updated)
      }

    case (ctx, HandleGridResults) =>
      ctx.log.info(s"Starting to handle grid results.")

      handleResults(
        runGuardianData.cfg.grids.output,
        finishedGridData.lvData,
        finishedGridData.mvData,
        finishedGridData.hvData,
        finishedGridData.mvNodeChanges,
        finishedGridData.assetInformation,
        childReferences.resultListeners,
        runGuardianData.msgAdapters
      )(ctx.log)

      stopping(stopChildren(runGuardianData.runId, childReferences, ctx))
    case (ctx, ResultEventListenerDied) =>
      // we wait for exact one listener as we only started one
      /* Start coordinated shutdown */
      ctx.log.info(
        s"Run ${runGuardianData.runId} finished. Stop all run-related processes."
      )
      stopping(stopChildren(runGuardianData.runId, childReferences, ctx))
    case (ctx, watch: RunWatch) =>
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
  ): Behavior[RunRequest] = Behaviors.receive {
    case (ctx, watch: RunWatch) =>
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
