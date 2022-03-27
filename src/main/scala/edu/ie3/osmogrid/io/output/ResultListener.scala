/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.io.output

import akka.actor.typed.{ActorRef, Behavior, PostStop}
import akka.actor.typed.scaladsl.Behaviors
import edu.ie3.datamodel.models.input.container.{
  GridContainer,
  JointGridContainer
}
import edu.ie3.osmogrid.ActorStopSupportStateless
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.guardian.OsmoGridGuardian
import edu.ie3.osmogrid.io.output.ResultListener.ResultEvent

import java.util.UUID

object ResultListener extends ActorStopSupportStateless[ResultEvent] {
  sealed trait Request
  final case class GridResult(
      grid: GridContainer,
      replyTo: ActorRef[Response]
  ) extends Request
      with ResultEvent
  object Terminate extends Request with ResultEvent

  sealed trait Response
  final case class ResultHandled(
      runId: UUID,
      replyTo: ActorRef[ResultListener.ResultEvent]
  ) extends Response

  /* internal API */
  sealed trait ResultEvent

  def apply(runId: UUID, cfg: OsmoGridConfig.Output): Behavior[ResultEvent] =
    Behaviors
      .receive[ResultEvent] {
        case (ctx, GridResult(grid, replyTo)) =>
          ctx.log.info(s"Received grid result for run id '${runId.toString}'")
          // TODO: Actual persistence and stuff, ...
          replyTo ! ResultHandled(runId, ctx.self)
          stopBehavior
        case (ctx, Terminate) => stopBehavior
      }
      .receiveSignal { case (ctx, PostStop) =>
        postStopCleanUp(ctx.log)
      }

  override protected def cleanUp(): Unit = {
    /* Nothing to do here. At least until now. */
  }
}
