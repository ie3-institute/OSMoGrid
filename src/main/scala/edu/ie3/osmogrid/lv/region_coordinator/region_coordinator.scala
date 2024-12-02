/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv.region_coordinator

import org.apache.pekko.actor.typed.ActorRef
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.io.input.AssetInformation
import edu.ie3.osmogrid.io.input.BoundaryAdminLevel.BoundaryAdminLevelValue
import edu.ie3.osmogrid.lv.LvGridResponse
import edu.ie3.osmogrid.model.OsmoGridModel.LvOsmoGridModel

import java.util.UUID

sealed trait LvRegionRequest

/** When receiving such message, the LvRegionCoordinator partitions given
  * OsmoGrid model by dividing its entities along the administrative boundaries
  * of given level
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
    lvCoordinatorRegionCoordinatorAdapter: ActorRef[LvRegionResponse],
    lvCoordinatorGridGeneratorAdapter: ActorRef[LvGridResponse],
) extends LvRegionRequest

sealed trait LvRegionResponse

final case class GridToExpect(gridUuid: UUID) extends LvRegionResponse
