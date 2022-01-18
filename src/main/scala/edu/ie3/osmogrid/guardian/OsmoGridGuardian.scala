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
import edu.ie3.osmogrid.guardian.OsmoGridGuardian.RunData.Stopping
import edu.ie3.osmogrid.io.input.InputDataProvider
import edu.ie3.osmogrid.io.output.ResultListener
import edu.ie3.osmogrid.io.output.ResultListener.{GridResult, Request}
import edu.ie3.osmogrid.lv.LvCoordinator
import edu.ie3.osmogrid.lv.LvCoordinator.ReqLvGrids
import org.slf4j.Logger

import java.util.UUID
import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Success, Try}

object OsmoGridGuardian
    extends RunSupport
    with StopSupport
    with SubGridHandling {

  /* Messages, that are understood and sent */
  sealed trait Request

  /** Message to initiate a grid generation run
    *
    * @param cfg
    *   Configuration for the tool
    * @param additionalListener
    *   Addresses of additional listeners to be informed about results
    * @param runId
    *   Unique identifier for that generation run
    */
  final case class Run(
      cfg: OsmoGridConfig,
      additionalListener: Seq[ActorRef[ResultListener.ResultEvent]] = Seq.empty,
      runId: UUID = UUID.randomUUID()
  ) extends Request

  /** Container object with all available adapters for outside protocol messages
    *
    * @param lvCoordinator
    *   Adapter for messages from [[LvCoordinator]]
    * @param resultListener
    *   Adapter for messages from [[ResultEventListener]]
    */
  private[guardian] final case class MessageAdapters(
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

  sealed trait Response

  /* dead watch events */
  sealed trait GuardianWatch extends Request {
    val runId: UUID
  }

  private[guardian] final case class InputDataProviderDied(runId: UUID)
      extends GuardianWatch

  private[guardian] final case class ResultEventListenerDied(runId: UUID)
      extends GuardianWatch

  private[guardian] final case class LvCoordinatorDied(runId: UUID)
      extends GuardianWatch

  /** Relevant, state-independent data, the the actor needs to know
    *
    * @param msgAdapters
    *   Collection of all message adapters
    * @param runs
    *   Currently active conversion runs
    */
  private[guardian] final case class GuardianData(
      msgAdapters: MessageAdapters,
      runs: Map[UUID, RunData]
  ) {

    def append(run: RunData): GuardianData =
      this.copy(runs = runs + (run.runId -> run))

    def replace(run: RunData): GuardianData = append(run)

    def remove(runId: UUID): GuardianData =
      this.copy(runs = runs - runId)
  }

  private[guardian] sealed trait RunData {
    val runId: UUID
    val cfg: OsmoGridConfig
  }
  private[guardian] case object RunData {
    def apply(
        run: Run,
        osmoGridResultEventListener: Option[ActorRef[
          ResultListener.ResultEvent
        ]],
        inputDataProvider: ActorRef[InputDataProvider.Request]
    ): RunData =
      Running(
        run.runId,
        run.cfg,
        osmoGridResultEventListener,
        run.additionalListener,
        inputDataProvider
      )

    /** Meta data regarding a certain given generation run, that yet is active
      *
      * @param runId
      *   Identifier of the run
      * @param cfg
      *   Configuration for that given run
      * @param osmoGridResultEventListener
      *   Reference to internal [[ResultListener]]
      * @param additionalResultListener
      *   References to additional [[ResultListener]]
      * @param inputDataProvider
      *   Reference to the input data provider
      */
    private[guardian] final case class Running(
        override val runId: UUID,
        override val cfg: OsmoGridConfig,
        osmoGridResultEventListener: Option[ActorRef[
          ResultListener.ResultEvent
        ]],
        private val additionalResultListener: Seq[
          ActorRef[ResultListener.ResultEvent]
        ],
        inputDataProvider: ActorRef[InputDataProvider.Request]
    ) extends RunData {
      def toStopping: Stopping =
        Stopping(runId, cfg)

      def resultListener: Seq[ActorRef[ResultListener.ResultEvent]] =
        osmoGridResultEventListener
          .map(Seq(_))
          .getOrElse(Seq.empty) ++ additionalResultListener
    }

    /** Meta data regarding a certain given generation run, that is scheduled to
      * be stopped
      *
      * @param runId
      *   Identifier of the run
      * @param cfg
      *   Configuration for that given run
      * @param resultListenerTerminated
      *   If the result listener yet has terminated
      * @param inputDataProviderTerminated
      *   If the input data provider yet has terminated
      */
    private[guardian] final case class Stopping(
        override val runId: UUID,
        override val cfg: OsmoGridConfig,
        resultListenerTerminated: Boolean = false,
        inputDataProviderTerminated: Boolean = false
    ) extends RunData {
      def successfullyTerminated: Boolean =
        resultListenerTerminated && inputDataProviderTerminated
    }
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

  private[guardian] def idle(guardianData: GuardianData): Behavior[Request] =
    Behaviors.receive {
      case (ctx, run: Run) =>
        initRun(run, ctx, guardianData.msgAdapters.lvCoordinator) match {
          case Success(runData) => idle(guardianData.append(runData))
          case Failure(exception) =>
            ctx.log.error(
              "Issuing of run failed. Keep on going without that run.",
              exception
            )
            idle(guardianData)
        }
      case (ctx, MessageAdapters.WrappedLvCoordinatorResponse(response)) =>
        response match {
          case LvCoordinator.RepLvGrids(runId, grids) =>
            handleLvResults(runId, grids, guardianData)(ctx.log)
            Behaviors.same
        }
      case (ctx, MessageAdapters.WrappedListenerResponse(response)) =>
        response match {
          case ResultListener.ResultHandled(runId) =>
            ctx.log.info(
              s"Results for run $runId handled successfully. Shutting down processes for this run."
            )
            idle(stopRunProcesses(guardianData, runId, ctx))
        }
      case (ctx, watch: GuardianWatch) =>
        /* Somebody died. Let's see, what we can do. */
        handleGuardianWatchEvent(watch, guardianData, ctx)
    }
}
