/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.io.output

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.{ActorContext, StashBuffer}
import edu.ie3.datamodel.models.input.container.JointGridContainer
import edu.ie3.osmogrid.cfg.OsmoGridConfig

import java.util.UUID

private sealed trait ResultListenerProtocol

object ResultListenerProtocol {

  // external protocol requests
  sealed trait Request extends ResultListenerProtocol

  final case class GridResult(
      grid: JointGridContainer,
      replyTo: ActorRef[ResultListenerProtocol.Response]
  ) extends Request
      with ResultListenerProtocol

  object Terminate extends Request // todo JH check if this can be removed

  // external protocol responses
  sealed trait Response

  final case class ResultHandled(
      runId: UUID,
      replyTo: ActorRef[ResultListenerProtocol.Request]
  ) // todo JH remove UUID, todo check if replyTo can be removed
      extends Response

  // internal API
  private[output] sealed trait PersistenceListenerEvent
      extends ResultListenerProtocol

  private[output] final case class InitComplete(stateData: ListenerStateData)
      extends PersistenceListenerEvent

  private[output] final case class InitFailed(cause: Throwable)
      extends PersistenceListenerEvent

  private[output] final case class ResultHandlingSucceeded(
      resultHandled: ResultHandled
  ) extends PersistenceListenerEvent

  private[output] final case class ResultHandlingFailed(cause: Throwable)
      extends PersistenceListenerEvent

  private[output] final case class ListenerStateData(
      runId: UUID,
      cfg: OsmoGridConfig.Output,
      ctx: ActorContext[ResultListenerProtocol],
      buffer: StashBuffer[ResultListenerProtocol],
      sink: ResultSink
  )

}
