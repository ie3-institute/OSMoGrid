/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv.region_coordinator

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.Behaviors
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.io.input.BoundaryAdminLevel
import edu.ie3.osmogrid.lv.MunicipalityCoordinator
import edu.ie3.osmogrid.lv.region_coordinator.{
  BoundaryFactory,
  OsmoGridModelPartitioner
}
import edu.ie3.osmogrid.model.OsmoGridModel.LvOsmoGridModel

object LvRegionCoordinator {

  sealed trait Request
  final case class Partition(
      osmoGridModel: LvOsmoGridModel,
      administrativeLevel: BoundaryAdminLevel,
      lvConfig: OsmoGridConfig.Generation.Lv,
      replyTo: ActorRef[Response]
  ) extends Request

  sealed trait Response
  case object Done extends Response
  final case class RepLvGrids(subGrids: Seq[SubGridContainer]) extends Response

  def apply(): Behaviors.Receive[Request] = idle()

  private def idle(): Behaviors.Receive[Request] = Behaviors.receive {
    (ctx, msg) =>
      msg match {
        case Partition(
              osmoGridModel,
              administrativeLevel,
              cfg,
              replyTo
            ) =>
          val areas =
            BoundaryFactory.buildBoundaryPolygons(
              osmoGridModel,
              administrativeLevel
            )

          val newOsmoGridModels =
            if areas.isEmpty then
              // if no containers have been found at this level, we continue with container of previous level
              Iterable.single(osmoGridModel)
            else
              OsmoGridModelPartitioner
                .partition(
                  osmoGridModel,
                  areas
                )
                .values

          val levels = BoundaryAdminLevel(cfg.boundaryAdminLevel.lowest)
            .zip(BoundaryAdminLevel.nextLowerLevel(administrativeLevel))
            .filter((lowestLevel, nextLevel) => nextLevel <= lowestLevel)

          newOsmoGridModels.iterator.foreach { osmoGridModel =>
            levels match {
              case Some(_, nextLevel) =>
                val newRegionCoordinator = ctx.spawnAnonymous(
                  LvRegionCoordinator()
                )
                newRegionCoordinator ! Partition(
                  osmoGridModel,
                  nextLevel,
                  cfg,
                  replyTo
                )
              case None =>
                ctx.spawnAnonymous(MunicipalityCoordinator.apply(osmoGridModel))
            }
          }

          Behaviors.same

        case unsupported =>
          ctx.log.warn(s"Received unsupported message '$unsupported'.")
          Behaviors.stopped
      }
  }

}
