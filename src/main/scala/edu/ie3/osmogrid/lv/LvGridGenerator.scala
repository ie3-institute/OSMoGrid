/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.LazyLogging
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.io.input.InputDataProvider.AssetInformation
import edu.ie3.osmogrid.lv.LvGraphGeneratorSupport.buildGridGraph
import edu.ie3.osmogrid.lv.LvGridGeneratorSupport.buildGrid
import edu.ie3.osmogrid.model.OsmoGridModel.LvOsmoGridModel
import edu.ie3.util.quantities.QuantityUtils.RichQuantityDouble
import tech.units.indriya.quantity.Quantities
import tech.units.indriya.unit.Units

object LvGridGenerator extends LazyLogging {
  sealed trait Request

  final case class GenerateGrid(
      osmData: LvOsmoGridModel,
      assetInformation: AssetInformation,
      config: OsmoGridConfig.Generation.Lv
  ) extends Request

  sealed trait Response

  final case class RepLvGrid(
      grid: SubGridContainer
  ) extends Response

  def apply(): Behaviors.Receive[Request] = idle

  private def idle: Behaviors.Receive[Request] = Behaviors.receive {
    case (
          ctx,
          GenerateGrid(
            osmData,
            assetInformation,
            config
          )
        ) =>
      val powerDensity = config.averagePowerDensity.asKiloWattPerSquareMetre
      val minDistance = Quantities.getQuantity(config.minDistance, Units.METRE)
      val (graph, buildingGraphConnections) =
        buildGridGraph(
          osmData,
          powerDensity,
          minDistance,
          config.considerHouseConnectionPoints
        )
      val lineType = ???
      val lvSubGrid = buildGrid(
        graph,
        buildingGraphConnections,
        config.ratedVoltage.asKiloVolt,
        config.considerHouseConnectionPoints,
        lineType,
        config.gridName
      )
      ???
    case (ctx, unsupported) =>
      ctx.log.warn(s"Received unsupported message '$unsupported'.")
      Behaviors.stopped
  }
}
