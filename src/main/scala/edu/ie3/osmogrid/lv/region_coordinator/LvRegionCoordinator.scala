/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */
package edu.ie3.osmogrid.lv.region_coordinator

import edu.ie3.osmogrid.io.input.BoundaryAdminLevel
import edu.ie3.osmogrid.lv.{GenerateLvGrid, LvGridGenerator}
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import java.util.UUID

object LvRegionCoordinator {

  def apply(): Behaviors.Receive[LvRegionRequest] = idle()

  private def idle(): Behaviors.Receive[LvRegionRequest] = Behaviors.receive {
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
            else {
              val partitionedAreas = OsmoGridModelPartitioner
                .partition(
                  osmoGridModel,
                  areas
                )
              if (partitionedAreas.isEmpty)
                Iterable.single(osmoGridModel)
              else partitionedAreas.values
            }

          val levels = BoundaryAdminLevel
            .get(cfg.boundaryAdminLevel.lowest)
            .zip(administrativeLevel.nextLowerLevel())
            .filter { case (lowest, next) =>
              lowest >= next
            }

          newOsmoGridModels.foreach { osmoGridModel =>
            levels match {
              case Some((_, nextLevel)) =>
                // Check if buildings or existing substations are empty
                val buildingsEmpty = osmoGridModel.buildings.isEmpty
                val substationsEmpty = osmoGridModel.existingSubstations.isEmpty

                // Skip spawning LvRegionCoordinator if both buildings and substations are empty
                if (!buildingsEmpty || !substationsEmpty) {
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
                }
              case None =>
                val gridGenerator = ctx.spawnAnonymous(LvGridGenerator())
                val gridUuid = UUID.randomUUID()
                lvCoordinatorRegionCoordinatorAdapter ! GridToExpect(gridUuid)
                gridGenerator ! GenerateLvGrid(
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
