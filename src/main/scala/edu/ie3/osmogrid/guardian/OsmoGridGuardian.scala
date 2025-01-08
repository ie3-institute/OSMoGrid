/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */
package edu.ie3.osmogrid.guardian

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import edu.ie3.osmogrid.guardian.run.RunGuardian

object OsmoGridGuardian {

  def apply(): Behavior[GuardianRequest] = idle(GuardianData.empty)

  private[guardian] def idle(
      guardianData: GuardianData
  ): Behavior[GuardianRequest] =
    Behaviors.receive {
      case (ctx, Run(cfg, additionalListener, runId)) =>
        val runGuardian = ctx.spawn(
          RunGuardian(cfg, additionalListener, runId),
          s"RunGuardian_$runId"
        )
        ctx.watchWith(runGuardian, RunGuardianDied(runId))
        runGuardian ! run.Run
        idle(guardianData.append(runId))

      case (ctx, watch: GuardianWatch) =>
        watch match {
          case RunGuardianDied(runId) =>
            ctx.log.info(s"Run $runId terminated.")

            val updatedGuardianData = guardianData.remove(runId)
            if (updatedGuardianData.isComplete)
              Behaviors.stopped
            else
              idle(updatedGuardianData)
        }
    }
}
