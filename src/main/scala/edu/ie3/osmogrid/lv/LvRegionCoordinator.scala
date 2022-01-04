/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorRef
import org.slf4j.Logger
import de.osmogrid.util.quantities.PowerDensity
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.osmogrid.exception.IllegalCalculationException
import edu.ie3.osmogrid.guardian.OsmoGridGuardian.RepLvGrids
import edu.ie3.osmogrid.lv.LvCoordinator
import edu.ie3.osmogrid.lv.LvCoordinator.LvCoordinatorEvent
import edu.ie3.osmogrid.lv.LvGenerator.LvGeneratorEvent
import edu.ie3.osmogrid.model.LoadLocation
import edu.ie3.osmogrid.model.WayUtils.RichClosedWay
import edu.ie3.util.geo.GeoUtils
import edu.ie3.util.osm.OsmEntities.Way
import edu.ie3.util.osm.{OsmEntities, OsmModel}
import tech.units.indriya.ComparableQuantity

import javax.measure.quantity.Power
import scala.util.{Failure, Success, Try}

object LvRegionCoordinator extends LoadCalculation with LoadClustering {
  sealed trait LvRegionCoordinatorEvent
  final case class ReqLvGrids(
      osmContainer: OsmModel,
      replyTo: ActorRef[LvCoordinatorEvent]
  ) extends LvRegionCoordinatorEvent

  def apply(
      lvGeneratorPool: ActorRef[LvGeneratorEvent],
      loadDensity: ComparableQuantity[PowerDensity],
      restrictSubgridsToLanduseAreas: Boolean
  ): Behaviors.Receive[LvRegionCoordinatorEvent] =
    idle(lvGeneratorPool, loadDensity, restrictSubgridsToLanduseAreas)

  private def idle(
      lvGeneratorPool: ActorRef[LvGeneratorEvent],
      loadDensity: ComparableQuantity[PowerDensity],
      restrictSubgridsToLanduseAreas: Boolean
  ): Behaviors.Receive[LvRegionCoordinatorEvent] = Behaviors.receive {
    case (ctx, ReqLvGrids(osmContainer, replyTo)) =>
      implicit val logger: Logger = ctx.log
      logger.debug("Received osm data for a given region. Start execution.")

      /* Determine the location of loads */
      val loadLocations = determineLoadLocations(osmContainer, loadDensity)

      clusterLoads(loadLocations, osmContainer, restrictSubgridsToLanduseAreas)

      /* TODO
          1) Cluster the given loads
          2) Generate sub-graphs based on clusters
          3) Distribute work to worker pool and collect the result
       */

      replyTo ! LvCoordinator.RepLvGrids(Vector.empty[SubGridContainer])
      Behaviors.same
    case (ctx, unsupported) =>
      ctx.log.warn(s"Received unsupported message '$unsupported'.")
      Behaviors.stopped
  }
}
