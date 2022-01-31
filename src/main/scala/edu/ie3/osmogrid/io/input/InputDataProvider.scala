/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.io.input

import akka.actor.typed.Behavior
import akka.actor.typed.ActorRef
import akka.actor.typed.PostStop
import akka.actor.typed.scaladsl.Behaviors
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.guardian.OsmoGridGuardian
import edu.ie3.osmogrid.lv.LvCoordinator.stopState
import edu.ie3.osmogrid.model.{OsmoGridModel, PbfFilter}
import org.slf4j.Logger

import java.util.UUID

object InputDataProvider {

  sealed trait Request

  final case class ReqOsm(
      runId: UUID,
      replyTo: ActorRef[InputDataProvider.Response],
      filter: PbfFilter
  ) extends Request

  final case class ReqAssetTypes(
      runId: UUID,
      replyTo: ActorRef[InputDataProvider.Response]
  ) extends Request

  object Terminate extends Request

  // external responses
  sealed trait Response
  final case class RepOsm(runId: UUID, osmModel: OsmoGridModel) extends Response
  final case class InvalidOsmRequest(reqRunId: UUID, actualRunId: UUID)
      extends Response
  final case class OsmReadFailed(reason: Throwable) extends Response
  final case class RepAssetTypes(osmModel: OsmoGridModel) extends Response

  def apply(cfg: OsmoGridConfig.Input): Behavior[Request] =
    Behaviors
      .receive[Request] {
        case (ctx, ReqOsm(runId, replyTo, filter)) =>
          ctx.log.warn("Reading of data not yet implemented.")
          Behaviors.same
        case (ctx, ReqAssetTypes(_, _)) =>
          ctx.log.info("Got request to provide asset types. But do nothing.")
          Behaviors.same
        case (ctx, Terminate) =>
          ctx.log.info("Stopping input data provider")
          stopState
      }
      .receiveSignal { case (ctx, PostStop) => postStopCleanUp(ctx.log) }

  /** Partial function to perform cleanup tasks while shutting down
    */
  private val cleanUp: () => Unit = ???

  /** Specific stop state with clean up actions issued
    */
  private val stopState: Behavior[Request] = Behaviors.stopped(cleanUp)

  private def postStopCleanUp(log: Logger) = {
    log.info("Got terminated by ActorSystem.")
    stopState
  }
}
