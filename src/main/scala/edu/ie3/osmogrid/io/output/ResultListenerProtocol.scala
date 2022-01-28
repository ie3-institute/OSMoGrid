/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.io.output

import akka.actor.typed.ActorRef
import edu.ie3.datamodel.models.input.container.JointGridContainer

import java.util.UUID

private trait ResultListenerProtocol

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
  sealed trait Response extends ResultListenerProtocol

  final case class ResultHandled(
      runId: UUID,
      replyTo: ActorRef[ResultListenerProtocol.Request]
  ) // todo JH remove UUID, todo check if replyTo can be removed
      extends Response

}
