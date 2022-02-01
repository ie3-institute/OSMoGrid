/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.guardian.run

import akka.actor.typed.ActorRef
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.io.input.InputDataProvider
import edu.ie3.osmogrid.io.output.ResultListener
import edu.ie3.osmogrid.lv.coordinator

import java.util.UUID

/* This file only contains package-level definitions */

/* Received requests */
sealed trait Request

object Run extends Request

/** Container object with all available adapters for outside protocol messages
  *
  * @param lvCoordinator
  *   Adapter for messages from [[LvCoordinator]]
  * @param resultListener
  *   Adapter for messages from [[ResultEventListener]]
  */
private final case class MessageAdapters(
    lvCoordinator: ActorRef[coordinator.Response],
    resultListener: ActorRef[ResultListener.Response]
)

private object MessageAdapters {
  final case class WrappedLvCoordinatorResponse(
      response: coordinator.Response
  ) extends Request

  final case class WrappedListenerResponse(
      response: ResultListener.Response
  ) extends Request
}

/* Death watch messages */
sealed trait Watch extends Request

object InputDataProviderDied extends Watch

object ResultEventListenerDied extends Watch

object LvCoordinatorDied extends Watch

/* Sent out responses */
sealed trait Response

final case class Done(runId: UUID) extends Response

private final case class ChildReferences(
    inputDataProvider: ActorRef[InputDataProvider.Request],
    resultListener: Option[ActorRef[ResultListener.ResultEvent]],
    additionalResultListeners: Seq[ActorRef[ResultListener.ResultEvent]],
    lvCoordinator: Option[ActorRef[coordinator.Request]]
) {
  def resultListeners: Seq[ActorRef[ResultListener.ResultEvent]] =
    resultListener
      .map(Seq(_))
      .getOrElse(Seq.empty) ++ additionalResultListeners
}

private sealed trait StateData
private final case class RunGuardianData(
    runId: UUID,
    cfg: OsmoGridConfig,
    additionalListener: Seq[ActorRef[ResultListener.ResultEvent]],
    msgAdapters: MessageAdapters
) extends StateData

/** Meta data to keep track of which children already terminated during the
  * coordinated shutdown phase
  *
  * @param runId
  *   Identifier of the run
  * @param inputDataProviderTerminated
  *   If the [[InputDataProvider]] has stopped
  * @param resultListenerTerminated
  *   If the [[ResultListener]] has stopped
  * @param lvCoordinatorTerminated
  *   Optional information, if the [[LvCoordinator]] has stopped
  */
private final case class StoppingData(
    runId: UUID,
    inputDataProviderTerminated: Boolean,
    resultListenerTerminated: Boolean,
    lvCoordinatorTerminated: Option[Boolean]
) extends StateData {
  def allChildrenTerminated: Boolean =
    inputDataProviderTerminated && resultListenerTerminated && lvCoordinatorTerminated
      .forall(terminated => terminated)
}
