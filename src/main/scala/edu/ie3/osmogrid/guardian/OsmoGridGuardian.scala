/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.guardian

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.guardian.run.RunGuardian
import edu.ie3.osmogrid.io.output.ResultListenerProtocol

import java.util.UUID

object OsmoGridGuardian {
  var CONFIG: OsmoGridConfig = _

  sealed trait Request

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
  ) extends Request

  /* dead watch events */
  sealed trait Watch extends Request {
    val runId: UUID
  }

  private[guardian] final case class RunGuardianDied(override val runId: UUID)
      extends Watch

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
  object GuardianData {
    def empty = new GuardianData(Seq.empty[UUID])
  }

  def apply(): Behavior[Request] = idle(GuardianData.empty)

  private[guardian] def idle(guardianData: GuardianData): Behavior[Request] =
    Behaviors.receive {
      case (ctx, Run(cfg, additionalListener, runId)) =>
        // setting up a copy of the config for global access
        CONFIG = cfg.copy()

        val runGuardian = ctx.spawn(
          RunGuardian(cfg, additionalListener, runId),
          s"RunGuardian_$runId"
        )
        ctx.watchWith(runGuardian, RunGuardianDied(runId))
        runGuardian ! run.Run
        idle(guardianData.append(runId))

      case (ctx, watch: Watch) =>
        watch match {
          case RunGuardianDied(runId) =>
            ctx.log.info(s"Run $runId terminated.")
            idle(guardianData.remove(runId))
        }
    }
}
