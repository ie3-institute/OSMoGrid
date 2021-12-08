/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorRef
import de.osmogrid.util.quantities.PowerDensity
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.osmogrid.guardian.OsmoGridGuardian.RepLvGrids
import edu.ie3.osmogrid.lv.LvCoordinator
import edu.ie3.osmogrid.lv.LvCoordinator.LvCoordinatorEvent
import edu.ie3.osmogrid.lv.LvGenerator.LvGeneratorEvent
import edu.ie3.util.osm.OsmModel
import tech.units.indriya.ComparableQuantity

object LvRegionCoordinator {
  sealed trait LvRegionCoordinatorEvent
  final case class ReqLvGrids(
      osmModel: OsmModel,
      replyTo: ActorRef[LvCoordinatorEvent]
  ) extends LvRegionCoordinatorEvent

  def apply(
      lvGeneratorPool: ActorRef[LvGeneratorEvent],
      loadDensity: ComparableQuantity[PowerDensity]
  ): Behaviors.Receive[LvRegionCoordinatorEvent] =
    idle(lvGeneratorPool, loadDensity)

  private def idle(
      lvGeneratorPool: ActorRef[LvGeneratorEvent],
      loadDensity: ComparableQuantity[PowerDensity]
  ): Behaviors.Receive[LvRegionCoordinatorEvent] = Behaviors.receive {
    case (ctx, ReqLvGrids(osmModel, replyTo)) =>
      ctx.log.debug("Received osm data for a given region. Start execution.")

      /* Determine the loads */
      determineLoads(osmModel, loadDensity)

      /* TODO
          1) Generate load model in the area of interest
          2) Cluster the given loads
          3) Generate sub-graphs based on clusters
          4) Distribute work to worker pool and collect the result
       */

      replyTo ! LvCoordinator.RepLvGrids(Vector.empty[SubGridContainer])
      Behaviors.same
    case (ctx, unsupported) =>
      ctx.log.warn(s"Received unsupported message '$unsupported'.")
      Behaviors.stopped
  }

  private def determineLoads(
      osmModel: OsmModel,
      loadDensity: ComparableQuantity[PowerDensity]
  ): Unit = {
    /* TODO
        1) Find ways and relations, that have the tag "building"
        2) Calculate their area and equivalent load (as per given load density)
        3) Transform to suitable data model
     */
  }
}
