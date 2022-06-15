/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import akka.actor.typed.scaladsl.Behaviors
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.util.quantities.interfaces.{Irradiance, PowerDensity}
import tech.units.indriya.ComparableQuantity
import edu.ie3.osmogrid.model.OsmoGridModel.LvOsmoGridModel

import javax.measure.quantity.{Area, Length, Power}

object LvGridGenerator extends GraphBuildingSupport {
  sealed trait Request
  final case class GenerateGrid(
      osmData: LvOsmoGridModel,
      powerDensity: Irradiance,
      minDistance: ComparableQuantity[Length]
  ) extends Request

  sealed trait Response
  final case class RepLvGrid(
      grid: SubGridContainer
  ) extends Response

  def apply(): Behaviors.Receive[Request] = idle

  private def idle: Behaviors.Receive[Request] = Behaviors.receive {
    case (ctx, GenerateGrid(osmData, powerDensity, minDistance)) =>
      val streetGraph = buildGridGraph(osmData, powerDensity, minDistance)
      ???
    case (ctx, unsupported) =>
      ctx.log.warn(s"Received unsupported message '$unsupported'.")
      Behaviors.stopped
  }
}
