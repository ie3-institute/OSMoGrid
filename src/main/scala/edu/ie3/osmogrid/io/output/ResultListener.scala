/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.io.output

import akka.actor.typed.Behavior
import edu.ie3.datamodel.models.input.container.GridContainer
import edu.ie3.osmogrid.cfg.OsmoGridConfig

object ResultListener {

  sealed trait ResultEvent

  final case class GridResult(grid: GridContainer) extends ResultEvent

  def apply(cfg: OsmoGridConfig.Output): Behavior[ResultEvent] = ???

}
