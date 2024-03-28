/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.guardian.run

import org.apache.pekko.actor.typed.ActorRef
import edu.ie3.datamodel.models.input.NodeInput
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.io.input.{AssetInformation, InputDataEvent}
import edu.ie3.osmogrid.io.output.{ResultListener, ResultListenerProtocol}
import edu.ie3.osmogrid.lv.{LvRequest, LvResponse}
import edu.ie3.osmogrid.mv.{MvRequest, MvResponse}

import java.util.UUID

/* This file only contains package-level definitions */

/* Received requests */
sealed trait RunRequest

object Run extends RunRequest

/** Container object with all available adapters for outside protocol messages
  *
  * @param lvCoordinator
  *   Adapter for messages from [[LvCoordinator]]
  * @param mvCoordinator
  *   Adapter for messages from [[MvCoordinator]]
  */
private[run] final case class MessageAdapters(
    lvCoordinator: ActorRef[LvResponse],
    mvCoordinator: ActorRef[MvResponse]
)

private[run] object MessageAdapters {
  final case class WrappedLvCoordinatorResponse(
      response: LvResponse
  ) extends RunRequest

  final case class WrappedMvCoordinatorResponse(
      response: MvResponse
  ) extends RunRequest
}

/* Death watch messages */
sealed trait RunWatch extends RunRequest

object InputDataProviderDied extends RunWatch

object ResultEventListenerDied extends RunWatch

object LvCoordinatorDied extends RunWatch

object MvCoordinatorDied extends RunWatch

/* Sent out responses */
sealed trait RunResponse

final case class Done(runId: UUID) extends RunResponse

/** Container object containing references to all child actors.
  * @param inputDataProvider
  *   reference of an actor that provides input data
  * @param resultListener
  *   option for a listener for result data
  * @param additionalResultListeners
  *   sequence of additional result data listeners
  * @param lvCoordinator
  *   option for a reference of a low voltage actor
  */
private[run] final case class ChildReferences(
    inputDataProvider: ActorRef[InputDataEvent],
    resultListener: Option[ActorRef[ResultListenerProtocol]],
    additionalResultListeners: Seq[ActorRef[ResultListenerProtocol]],
    lvCoordinator: Option[ActorRef[LvRequest]],
    mvCoordinator: Option[ActorRef[MvRequest]]
) {
  def resultListeners: Seq[ActorRef[ResultListenerProtocol]] =
    resultListener
      .map(Seq(_))
      .getOrElse(Seq.empty) ++ additionalResultListeners
}

sealed trait StateData

private[run] final case class RunGuardianData(
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
private[run] final case class StoppingData(
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
    assetInformation: AssetInformation
) extends StateData {
  def receivedAllData: Boolean = {
    val lv = lvExpected == lvData.isDefined
    val mv = mvExpected == mvData.isDefined

    lv && mv
  }
}

object FinishedGridData {
  def empty(lvExpected: Boolean, mvExpected: Boolean): FinishedGridData =
    FinishedGridData(
      lvExpected,
      mvExpected,
      None,
      None,
      AssetInformation.apply(Seq.empty, Seq.empty, Seq.empty)
    )
}

/** Message to tell the [[RunGuardian]] to start handling the received results.
  */
object HandleGridResults extends RunRequest
