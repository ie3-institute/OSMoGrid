/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.guardian.run

import akka.actor.typed.ActorRef
import edu.ie3.datamodel.models.input.container.{
  JointGridContainer,
  SubGridContainer
}
import edu.ie3.datamodel.utils.ContainerUtils
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.guardian.run.RunGuardian
import edu.ie3.osmogrid.guardian.run.SubGridHandling.assignSubnetNumbers
import edu.ie3.osmogrid.io.output.{ResultListener, ResultListenerProtocol}
import org.slf4j.Logger

import java.util.UUID
import scala.jdk.CollectionConverters.*

trait SubGridHandling {

  /** Handle incoming low voltage grid results
    *
    * @param grids
    *   Received grids
    * @param cfg
    *   Grid generation config
    */
  protected def handleLvResults(
      grids: Seq[SubGridContainer],
      cfg: OsmoGridConfig.Generation
  )(implicit log: Logger): JointGridContainer = {
    log.info("All lv grids successfully generated.")
    val updatedSubGrids = assignSubnetNumbers(grids)

    log.debug(
      "No further generation steps intended. Hand over results to result handler."
    )
    /* Bundle grid result and inform interested listeners */
    // todo JH spin up listeners
//    val jointGrid =
    ContainerUtils.combineToJointGrid(updatedSubGrids.asJava)
//    resultListener.foreach { listener =>
//      listener ! ResultListenerProtocol.GridResult(
//        jointGrid,
//        msgAdapters.resultListener
//      )
//    }
  }
}

object SubGridHandling {
  private def assignSubnetNumbers(
      subnets: Seq[SubGridContainer]
  ): Seq[SubGridContainer] = subnets.zipWithIndex.map {
    case (subGrid, subnetNumber) =>
      assignSubnetNumber(subGrid, subnetNumber + 1)
  }

  private def assignSubnetNumber(subGrid: SubGridContainer, subnetNumber: Int) =
    subGrid
}
