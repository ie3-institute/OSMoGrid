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
import edu.ie3.osmogrid.guardian.OsmoGridGuardian.stopChildrenByRun
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
    */
  private final case class GuardianData(
      msgAdapters: MessageAdapters,
      runs: Map[UUID, RunData]
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

    idle(GuardianData(messageAdapters, Map.empty))
  }

  private def idle(guardianData: GuardianData): Behavior[Request] =
    Behaviors.receive {
      case (ctx, run: Run) =>
        val runData = initRun(run, ctx, guardianData.msgAdapters.lvCoordinator)
        idle(guardianData.append(runData))
      case (ctx, unsupported) =>
        ctx.log.error(s"Received unsupported message '$unsupported'.")
        Behaviors.stopped
    }

  private def initRun(
      run: Run,
      ctx: ActorContext[Request],
      lvCoordinatorAdapter: ActorRef[LvCoordinator.Response]
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
            ResultListener(run.runId, run.cfg.output),
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
        lvCoordinator ! ReqLvGrids(run.runId, lvConfig, lvCoordinatorAdapter)
      case unsupported =>
        ctx.log.error(
          s"Received unsupported grid generation config '$unsupported'. Stopping run with id '${run.runId}'!"
        )
        stopChildrenByRun(
          RunData(run, resultEventListener, inputProvider),
          ctx
        )
    }

    RunData(run, resultEventListener, inputProvider)
  }

  private def stopChildrenByRun(
      runData: RunData,
      ctx: ActorContext[Request]
  ): Unit = {
    ctx.unwatch(runData.inputDataProvider)
    ctx.stop(runData.inputDataProvider)
    runData.resultEventListener.foreach(ctx.unwatch)
    runData.resultEventListener.foreach(ctx.stop)
  }

}
