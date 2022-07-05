/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.LazyLogging
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.osmogrid.lv.LvGraphBuilder.buildGridGraph
import edu.ie3.util.quantities.interfaces.Irradiance
import tech.units.indriya.ComparableQuantity
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.model.OsmoGridModel.LvOsmoGridModel
import javax.measure.quantity.Length

object LvGridGenerator extends GridBuildingSupport with LazyLogging {
  sealed trait Request
  final case class GenerateGrid(
      osmData: LvOsmoGridModel,
      powerDensity: Irradiance,
      minDistance: ComparableQuantity[Length],
      config: OsmoGridConfig
  ) extends Request

  sealed trait Response
  final case class RepLvGrid(
      grid: SubGridContainer
  ) extends Response

  def apply(): Behaviors.Receive[Request] = idle

  private def idle: Behaviors.Receive[Request] = Behaviors.receive {
    case (ctx, GenerateGrid(osmData, powerDensity, minDistance, config)) =>
      val (graph, buildingGraphConnections) =
        buildGridGraph(
          osmData,
          powerDensity,
          minDistance,
          config.lvGrid.considerHouseConnectionPoints
        )
      // todo : ask input data provider for line type
      val lineType = ???
      val lvSubGrid = buildGrid(
        graph,
        buildingGraphConnections,
        config,
        lineType
      )
      ???
    case (ctx, unsupported) =>
      ctx.log.warn(s"Received unsupported message '$unsupported'.")
      Behaviors.stopped
  }
}
