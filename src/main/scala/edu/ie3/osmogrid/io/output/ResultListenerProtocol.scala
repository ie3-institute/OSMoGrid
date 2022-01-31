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
      grid: JointGridContainer
  ) extends Request
      with ResultListenerProtocol

  // internal API
  private[output] sealed trait PersistenceListenerEvent
      extends ResultListenerProtocol

  private[output] object PersistenceListenerEvent {

    final case class InitComplete(stateData: ListenerStateData)
        extends PersistenceListenerEvent

    final case class InitFailed(cause: Throwable)
        extends PersistenceListenerEvent

    case object ResultHandlingSucceeded extends PersistenceListenerEvent
    final case class ResultHandlingFailed(cause: Throwable)
        extends PersistenceListenerEvent
  }

  // state data
  private[output] final case class ListenerStateData(
      runId: UUID,
      ctx: ActorContext[ResultListenerProtocol],
      buffer: StashBuffer[ResultListenerProtocol],
      sink: ResultSink
  )

}
