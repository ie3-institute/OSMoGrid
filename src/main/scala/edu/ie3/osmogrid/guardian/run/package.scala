/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.guardian

import akka.actor.typed.ActorRef
import edu.ie3.osmogrid.io.input.InputDataProvider
import edu.ie3.osmogrid.io.output.ResultListener
import edu.ie3.osmogrid.lv.LvCoordinator

import java.util.UUID

package object run {
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

  final case class Done(runId: UUID) extends Response

  sealed trait Watch extends Request

  private object InputDataProviderDied extends Watch

  private object ResultEventListenerDied extends Watch

  private object LvCoordinatorDied extends Watch

  private final case class ChildReferences(
      inputDataProvider: ActorRef[InputDataProvider.Request],
      resultListener: Option[ActorRef[ResultListener.ResultEvent]],
      additionalResultListeners: Seq[ActorRef[ResultListener.ResultEvent]],
      lvCoordinator: Option[ActorRef[LvCoordinator.Request]]
  ) {
    def resultListeners: Seq[ActorRef[ResultListener.ResultEvent]] =
      resultListener
        .map(Seq(_))
        .getOrElse(Seq.empty) ++ additionalResultListeners
  }

  /** Meta data to keep track of which children already terminated during the
    * coordinated shutdown phase
    *
    * @param inputDataProviderTerminated
    *   If the [[InputDataProvider]] has stopped
    * @param resultListenerTerminated
    *   If the [[ResultListener]] has stopped
    * @param lvCoordinatorTerminated
    *   Optional information, if the [[LvCoordinator]] has stopped
    */
  private final case class StoppingData(
      inputDataProviderTerminated: Boolean,
      resultListenerTerminated: Boolean,
      lvCoordinatorTerminated: Option[Boolean]
  ) {
    def allChildrenTerminated: Boolean =
      inputDataProviderTerminated && resultListenerTerminated && lvCoordinatorTerminated
        .contains(true)
  }
}
