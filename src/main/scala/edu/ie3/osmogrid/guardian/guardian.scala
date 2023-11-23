/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.guardian

import akka.actor.typed.ActorRef
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.io.output.ResultListenerProtocol

import java.util.UUID

sealed trait GuardianRequest

/** Message to initiate a grid generation run
  *
  * @param cfg
  *   Configuration for the tool
  * @param additionalListener
  *   Addresses of additional listeners to be informed about results
  * @param runId
  *   Unique identifier for that generation run
  */
final case class Run(
    cfg: OsmoGridConfig,
    additionalListener: Seq[ActorRef[ResultListenerProtocol]] = Seq.empty,
    runId: UUID = UUID.randomUUID()
) extends GuardianRequest

/* dead watch events */
sealed trait GuardianWatch extends GuardianRequest {
  val runId: UUID
}

private[guardian] final case class RunGuardianDied(override val runId: UUID)
    extends GuardianWatch

/** Relevant, state-independent data, the the actor needs to know
  *
  * @param runs
  *   Currently active conversion runs
  */
private[guardian] final case class GuardianData(
    runs: Seq[UUID]
) {
  def append(run: UUID): GuardianData = this.copy(runs = runs :+ run)

  def remove(run: UUID): GuardianData =
    this.copy(runs = runs.filterNot(_ == run))
}

private[guardian] object GuardianData {
  def empty = new GuardianData(Seq.empty[UUID])
}
