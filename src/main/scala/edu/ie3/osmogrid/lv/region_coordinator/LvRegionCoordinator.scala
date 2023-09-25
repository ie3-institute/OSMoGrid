/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv.region_coordinator

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.Behaviors
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.io.input.BoundaryAdminLevel
import edu.ie3.osmogrid.io.input.BoundaryAdminLevel.BoundaryAdminLevelValue
import edu.ie3.osmogrid.io.input.AssetInformation
import edu.ie3.osmogrid.lv.LvGridGenerator
import edu.ie3.osmogrid.lv.LvGridGenerator.GenerateGrid
import edu.ie3.osmogrid.model.OsmoGridModel.LvOsmoGridModel

import java.util.UUID

object LvRegionCoordinator {

  sealed trait Request

  /** When receiving such message, the LvRegionCoordinator partitions given
    * OsmoGrid model by dividing its entities along the administrative
    * boundaries of given level
    *
    * @param osmoGridModel
    *   The OsmoGrid model to partition
    * @param assetInformation
    *   The asset type information with which to build the grid
    * @param administrativeLevel
    *   The administrative boundary level at which to partition
    * @param lvConfig
    *   The configuration for lv grid generation
    * @param lvCoordinatorRegionCoordinatorAdapter
    *   The actor reference that will be used to communicate expected grids
    * @param lvCoordinatorGridGeneratorAdapter
    *   The actor reference that will be used to send the generated grid to
    */
  final case class Partition(
      osmoGridModel: LvOsmoGridModel,
      assetInformation: AssetInformation,
      administrativeLevel: BoundaryAdminLevelValue,
      lvConfig: OsmoGridConfig.Generation.Lv,
      lvCoordinatorRegionCoordinatorAdapter: ActorRef[
        LvRegionCoordinator.Response
      ],
      lvCoordinatorGridGeneratorAdapter: ActorRef[LvGridGenerator.Response]
  ) extends Request

  sealed trait Response

  final case class GridToExpect(gridUuid: UUID) extends Response

  def apply(): Behaviors.Receive[Request] = idle()

  private def idle(): Behaviors.Receive[Request] = Behaviors.receive {
    (ctx, msg) =>
      msg match {
        case Partition(
              osmoGridModel,
              assetInformation,
              administrativeLevel,
              cfg,
              lvCoordinatorRegionCoordinatorAdapter,
              lvCoordinatorGridGeneratorAdapter
            ) =>
          val areas =
            BoundaryFactory.buildBoundaryPolygons(
              osmoGridModel,
              administrativeLevel
            )

          val newOsmoGridModels =
            if (areas.isEmpty)
              // if no containers have been found at this level, we continue with container of previous level
              Iterable.single(osmoGridModel)
            else
              OsmoGridModelPartitioner
                .partition(
                  osmoGridModel,
                  areas
                )
                .values

          val levels = BoundaryAdminLevel
            .get(cfg.boundaryAdminLevel.lowest)
            .zip(administrativeLevel.nextLowerLevel())
            .filter { case (lowest, next) =>
              lowest >= next
            }

          newOsmoGridModels.iterator.foreach { osmoGridModel =>
            levels match {
              case Some((_, nextLevel)) =>
                val newRegionCoordinator = ctx.spawnAnonymous(
                  LvRegionCoordinator()
                )
                newRegionCoordinator ! Partition(
                  osmoGridModel,
                  assetInformation,
                  nextLevel,
                  cfg,
                  lvCoordinatorRegionCoordinatorAdapter,
                  lvCoordinatorGridGeneratorAdapter
                )
              case None =>
                val gridGenerator = ctx.spawnAnonymous(LvGridGenerator())
                val gridUuid = UUID.randomUUID()
                lvCoordinatorRegionCoordinatorAdapter ! GridToExpect(gridUuid)
                gridGenerator ! GenerateGrid(
                  lvCoordinatorGridGeneratorAdapter,
                  gridUuid,
                  osmoGridModel,
                  assetInformation,
                  cfg
                )
            }
          }

          Behaviors.same

        case unsupported =>
          ctx.log.warn(s"Received unsupported message '$unsupported'.")
          Behaviors.stopped
      }
  }

}
