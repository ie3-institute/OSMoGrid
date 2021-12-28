/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.guardian

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.ActorRef
import edu.ie3.datamodel.models.input.container.{
  JointGridContainer,
  SubGridContainer
}
import edu.ie3.datamodel.utils.ContainerUtils
import edu.ie3.osmogrid.cfg.{ConfigFailFast, OsmoGridConfig}
import edu.ie3.osmogrid.cfg.OsmoGridConfig.{Generation, Output}
import edu.ie3.osmogrid.io.input.InputDataProvider
import edu.ie3.osmogrid.io.input.InputDataProvider.{InputDataEvent, Terminate}
import edu.ie3.osmogrid.io.output.PersistenceResultListener
import edu.ie3.osmogrid.io.output.PersistenceResultListener.{
  GridResult,
  PersistenceSuccessful,
  ResultEvent
}
import edu.ie3.osmogrid.lv.LvCoordinator
import edu.ie3.osmogrid.lv.LvCoordinator.ReqLvGrids
import org.slf4j.Logger

import java.util.UUID
import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Success, Try}

object OsmoGridGuardian {

  sealed trait OsmoGridGuardianEvent

  final case class Run(
      cfg: OsmoGridConfig,
      additionalListener: Vector[ActorRef[ResultEvent]] = Vector.empty,
      runId: UUID = UUID.randomUUID()
  ) extends OsmoGridGuardianEvent

  final case class RepLvGrids(runId: UUID, grids: Vector[SubGridContainer])
      extends OsmoGridGuardianEvent

  // external protocol
  private final case class WrappedListenerResponse(
      response: PersistenceResultListener.ListenerResponse
  ) extends OsmoGridGuardianEvent

  // dead watch events
  sealed trait GuardianWatch extends OsmoGridGuardianEvent {
    val runId: UUID
  }

  private final case class InputDataProviderDied(runId: UUID)
      extends GuardianWatch

  private final case class ResultEventListenerDied(runId: UUID)
      extends GuardianWatch

  private final case class LvCoordinatorDied(runId: UUID) extends GuardianWatch

  // actor data
  private final case class GuardianData(
      runs: Map[UUID, RunData],
      persistenceResponseMapper: ActorRef[
        PersistenceResultListener.ListenerResponse
      ]
  ) {

    def append(run: RunData): GuardianData =
      this.copy(runs = runs + (run.runId -> run))

    def remove(runId: UUID): GuardianData =
      this.copy(runs = runs - runId)
  }

  private final case class RunData(
      runId: UUID,
      cfg: OsmoGridConfig,
      resultEventListener: Vector[ActorRef[ResultEvent]],
      inputDataProvider: ActorRef[InputDataEvent]
  )

  private case object RunData {
    def apply(
        run: Run,
        resultEventListener: Vector[ActorRef[ResultEvent]],
        inputDataProvider: ActorRef[InputDataEvent]
    ): RunData =
      RunData(
        run.runId,
        run.cfg,
        run.additionalListener ++ resultEventListener,
        inputDataProvider
      )
  }

  def apply(): Behavior[OsmoGridGuardianEvent] =
    Behaviors.setup[OsmoGridGuardianEvent] { ctx =>
      // register external protocol mapper
      val persistenceResponseMapper =
        ctx.messageAdapter(rsp => WrappedListenerResponse(rsp))

      idle(GuardianData(Map.empty, persistenceResponseMapper))
    }

  private def idle(
      guardianData: GuardianData
  ): Behavior[OsmoGridGuardianEvent] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case run: Run =>
          val runData = initRun(run, ctx)
          idle(guardianData.append(runData))
        case repl @ RepLvGrids(runId, lvGrids) =>
          ctx.log.info(
            s"Received ${lvGrids.length} lv grids for run $runId. Try to join them ..."
          )
          handleLvReply(repl, guardianData, ctx)
        case wrapped: WrappedListenerResponse =>
          wrapped.response match {
            case PersistenceSuccessful(runId) =>
              ctx.log.info(s"Successfully persisted grid data from run $runId")
              Behaviors.same
          }
        case watch: GuardianWatch =>
          ctx.log.error(
            s"Received dead message '$watch' for run ${watch.runId}! " +
              s"Stopping all corresponding children ..."
          )
          guardianData.runs.get(watch.runId).foreach(stopChildrenByRun(_, ctx))
          idle(guardianData.remove(watch.runId))
      }
    }

  private def initRun(
      run: Run,
      ctx: ActorContext[OsmoGridGuardianEvent]
  ): RunData = {
    val log = ctx.log
    ConfigFailFast.check(run.cfg, run.additionalListener)
    log.info(s"Initializing grid generation for run with id '${run.runId}'!")

    log.info("Starting input data provider ...")
    val inputProvider =
      ctx.spawn(InputDataProvider(run.cfg.input), "InputDataProvider")
    ctx.watchWith(inputProvider, InputDataProviderDied(run.runId))
    val resultEventListener = run.cfg.output match {
      case Output(Some(_)) =>
        log.info("Starting output data listener ...")
        Vector(
          ctx.spawn(
            PersistenceResultListener(run.runId, run.cfg.output),
            "PersistenceResultListener"
          )
        )
      case Output(None) =>
        Vector.empty
    }
    resultEventListener.foreach(
      ctx.watchWith(_, ResultEventListenerDied(run.runId))
    )

    /* Check, which voltage level configs are given. Start with lv level, if this is desired for. */
    run.cfg.generation match {
      case Generation(Some(lvConfig)) =>
        ctx.log.info("Starting low voltage grid coordinator ...")
        val lvCoordinator = ctx.spawn(LvCoordinator(), "LvCoordinator")
        ctx.watchWith(lvCoordinator, LvCoordinatorDied(run.runId))
        ctx.log.info("Starting voltage level grid generation ...")
        lvCoordinator ! ReqLvGrids(run.runId, lvConfig, ctx.self)
      case unsupported =>
        ctx.log.error(
          s"Received unsupported grid generation config '$unsupported'. Stopping run with id '${run.runId}'!"
        )
        stopChildrenByRun(
          RunData.apply(run, resultEventListener, inputProvider),
          ctx
        )
    }

    RunData.apply(run, resultEventListener, inputProvider)
  }

  private def handleLvReply(
      reply: RepLvGrids,
      guardianData: GuardianData,
      ctx: ActorContext[OsmoGridGuardianEvent]
  ) = {
    Try(ContainerUtils.combineToJointGrid(reply.grids.asJava)) match {
      case Success(jointGrid) =>
        guardianData.runs
          .get(reply.runId)
          .foreach(
            _.resultEventListener
              .foreach(
                _ ! GridResult(
                  jointGrid,
                  guardianData.persistenceResponseMapper
                )
              )
          )
        idle(guardianData.remove(reply.runId))
      case Failure(exception) =>
        ctx.log.error(
          s"Combination of received sub-grids failed for run '${reply.runId}'. Shutting down this run!.",
          exception
        )
        guardianData.runs.get(reply.runId).foreach(stopChildrenByRun(_, ctx))
        idle(guardianData.remove(reply.runId))
    }
  }

  private def stopChildrenByRun(
      runData: RunData,
      ctx: ActorContext[OsmoGridGuardianEvent]
  ): Unit = {
    ctx.unwatch(runData.inputDataProvider)
    ctx.stop(runData.inputDataProvider)
    runData.resultEventListener.foreach(ctx.unwatch)
    runData.resultEventListener.foreach(ctx.stop)
  }

}
