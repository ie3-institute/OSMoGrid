/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.io.output

import akka.actor.typed.Behavior
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.Behaviors
import edu.ie3.datamodel.models.input.container.{
  GridContainer,
  JointGridContainer
}
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.guardian.OsmoGridGuardian

import java.util.UUID

object ResultListener {
  sealed trait Request
  final case class GridResult(
      grid: JointGridContainer,
      replyTo: ActorRef[OsmoGridGuardian.Request]
  ) extends Request
      with ResultEvent

  sealed trait Response

  /* internal API */
  sealed trait ResultEvent

  def apply(runId: UUID, cfg: OsmoGridConfig.Output): Behavior[ResultEvent] =
    Behaviors.receive { case (ctx, GridResult(grid, _)) =>
      ctx.log.info(s"Received grid result for grid '${grid.getGridName}'")
      // TODO: Actual persistence and stuff, closing sinks, ...
      Behaviors.stopped
    }

}
