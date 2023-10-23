/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.guardian.run

import akka.actor.typed.ActorRef
import edu.ie3.datamodel.models.input.NodeInput
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.io.input
import edu.ie3.osmogrid.io.output.{ResultListener, ResultListenerProtocol}
import edu.ie3.osmogrid.lv.coordinator
import edu.ie3.osmogrid.messages.{MvRequest, MvResponse}

import java.util.UUID

/* This file only contains package-level definitions */

/* Received requests */
sealed trait Request

object Run extends Request

/** Container object with all available adapters for outside protocol messages
  *
  * @param lvCoordinator
  *   Adapter for messages from [[LvCoordinator]]
  * @param mvCoordinator
  *   Adapter for messages from [[MvCoordinator]]
  */
private final case class MessageAdapters(
    lvCoordinator: ActorRef[coordinator.Response],
    mvCoordinator: ActorRef[MvResponse]
)

private object MessageAdapters {
  final case class WrappedLvCoordinatorResponse(
      response: coordinator.Response
  ) extends Request

  final case class WrappedMvCoordinatorResponse(
      response: MvResponse
  ) extends Request
}

/* Death watch messages */
sealed trait Watch extends Request

object InputDataProviderDied extends Watch

object ResultEventListenerDied extends Watch

object LvCoordinatorDied extends Watch

object MvCoordinatorDied extends Watch

/* Sent out responses */
sealed trait Response

final case class Done(runId: UUID) extends Response

final case class ChildReferences(
    inputDataProvider: ActorRef[input.InputDataEvent],
    resultListener: Option[ActorRef[ResultListenerProtocol]],
    additionalResultListeners: Seq[ActorRef[ResultListenerProtocol]],
    lvCoordinator: Option[ActorRef[coordinator.Request]],
    mvCoordinator: Option[ActorRef[MvRequest]]
) {
  def resultListeners: Seq[ActorRef[ResultListenerProtocol]] =
    resultListener
      .map(Seq(_))
      .getOrElse(Seq.empty) ++ additionalResultListeners
}

sealed trait StateData
private final case class RunGuardianData(
    runId: UUID,
    cfg: OsmoGridConfig,
    additionalListener: Seq[ActorRef[ResultListenerProtocol]],
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
final case class StoppingData(
    runId: UUID,
    inputDataProviderTerminated: Boolean,
    resultListenerTerminated: Boolean,
    lvCoordinatorTerminated: Option[Boolean],
    mvCoordinatorTerminated: Option[Boolean]
) extends StateData {
  def allChildrenTerminated: Boolean =
    inputDataProviderTerminated && resultListenerTerminated && lvCoordinatorTerminated
      .forall(terminated => terminated) && mvCoordinatorTerminated.forall(
      terminated => terminated
    )
}

final case class FinishedGridData(
    lvExpected: Boolean,
    mvExpected: Boolean,
    lvData: Option[Seq[SubGridContainer]],
    mvData: Option[Seq[SubGridContainer]],
    toBeUpdated: Option[Seq[NodeInput]]
) extends StateData {
  def receivedAllData: Boolean = {
    val lv = lvExpected == lvData.isDefined
    val mv = mvExpected == mvData.isDefined

    lv && mv
  }
}

object FinishedGridData {
  def empty(lvExpected: Boolean, mvExpected: Boolean): FinishedGridData =
    FinishedGridData(lvExpected, mvExpected, None, None, None)
}

/** Message to tell the [[RunGuardian]] to start handling the received results.
  */
object HandleGridResults extends Request
