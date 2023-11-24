/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import org.apache.pekko.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.LazyLogging
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.cfg.OsmoGridConfig.Voltage
import edu.ie3.osmogrid.exception.IllegalStateException
import edu.ie3.osmogrid.guardian.run.RunGuardian
import edu.ie3.osmogrid.io.input.AssetInformation
import edu.ie3.osmogrid.lv.LvGraphGeneratorSupport.buildConnectedGridGraphs
import edu.ie3.osmogrid.lv.LvGridGeneratorSupport.buildGrid
import edu.ie3.util.quantities.QuantityUtils.RichQuantityDouble
import tech.units.indriya.quantity.Quantities
import tech.units.indriya.unit.Units

import scala.collection.parallel.CollectionConverters.ImmutableSeqIsParallelizable

object LvGridGenerator extends LazyLogging {
  val voltages: Voltage = RunGuardian.getVoltageConfig

  def apply(): Behaviors.Receive[LvGridRequest] = idle

  private def idle: Behaviors.Receive[LvGridRequest] = Behaviors.receive {
    case (
          ctx,
          GenerateLvGrid(
            replyTo,
            gridUuid,
            osmData,
            assetInformation,
            config
          )
        ) =>
      ctx.log.info(s"Received request to generate grid: $gridUuid")
      val powerDensity = config.averagePowerDensity.asKiloWattPerSquareMetre
      val minDistance = Quantities.getQuantity(config.minDistance, Units.METRE)
      val connectedGridGraphs =
        buildConnectedGridGraphs(
          osmData,
          powerDensity,
          minDistance,
          config.considerHouseConnectionPoints
        )
      val lineType = assetInformation.lineTypes.headOption.getOrElse(
        throw IllegalStateException(
          "There are no line types within received asset types. Can not build the grid!"
        )
      )

      ctx.log.info(
        s"Finished building of grid graph. Starting to build electrical grid for grid: $gridUuid"
      )
      val lvSubGrids = connectedGridGraphs.map {
        case (graph, buildingGraphConnections) =>
          buildGrid(
            graph,
            buildingGraphConnections.par,
            voltages.lv.default.asKiloVolt,
            config.considerHouseConnectionPoints,
            lineType,
            gridUuid.toString
          )
      }

      ctx.log.info(
        s"Finished grid generation and sending results for grid: $gridUuid"
      )
      replyTo ! RepLvGrid(gridUuid, lvSubGrids.flatten)
      Behaviors.stopped
    case (ctx, unsupported) =>
      ctx.log.warn(s"Received unsupported message '$unsupported'.")
      Behaviors.stopped
  }
}
