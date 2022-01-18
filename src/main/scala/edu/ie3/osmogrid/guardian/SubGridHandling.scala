/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.guardian

import akka.actor.typed.scaladsl.ActorContext
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.datamodel.utils.ContainerUtils
import edu.ie3.osmogrid.guardian.OsmoGridGuardian.RunData.Running
import edu.ie3.osmogrid.guardian.OsmoGridGuardian.{
  GuardianData,
  Request,
  RunData
}
import edu.ie3.osmogrid.io.output.ResultListener

import java.util.UUID
import scala.jdk.CollectionConverters.*

trait SubGridHandling {

  /** Handle incoming low voltage grid results
    *
    * @param runId
    *   Reference to the current run
    * @param grids
    *   Received grids
    * @param guardianData
    *   Relevant, state-independent data, the the actor needs to know
    * @param ctx
    *   Current actor context
    */
  protected def handleLvResults(
      runId: UUID,
      grids: Seq[SubGridContainer],
      guardianData: GuardianData,
      ctx: ActorContext[Request]
  ): Unit = {
    ctx.log.info("All lv grids successfully generated.")
    val updatedSubGrids = assignSubnetNumbers(grids)

    guardianData.runs.get(runId) match {
      case Some(
            runData @ RunData.Running(runId, cfg, _, _, inputDataProvider)
          ) =>
        // TODO: Check for mv config and issue run there, if applicable
        ctx.log.debug(
          "No further generation steps intended. Hand over results to result handler."
        )
        /* Bundle grid result and inform interested listeners */
        val jointGrid =
          ContainerUtils.combineToJointGrid(updatedSubGrids.asJava)
        runData.resultListener.foreach { listener =>
          listener ! ResultListener.GridResult(
            jointGrid,
            guardianData.msgAdapters.resultListener
          )
        }
      case None =>
        ctx.log.error(
          s"Cannot find run information for '$runId', although it is supposed to be active. No further actions taken."
        )
    }
  }

  private def assignSubnetNumbers(
      subnets: Seq[SubGridContainer]
  ): Seq[SubGridContainer] = subnets.zipWithIndex.map {
    case (subGrid, subnetNumber) =>
      assignSubnetNumber(subGrid, subnetNumber + 1)
  }

  private def assignSubnetNumber(subGrid: SubGridContainer, subnetNumber: Int) =
    subGrid
}
