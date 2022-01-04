/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import de.osmogrid.util.quantities.PowerDensity
import edu.ie3.osmogrid.exception.IllegalCalculationException
import edu.ie3.osmogrid.model.LoadLocation
import edu.ie3.osmogrid.model.WayUtils.RichClosedWay
import edu.ie3.util.osm.OsmEntities.Way
import edu.ie3.util.osm.{OsmEntities, OsmModel}
import org.slf4j.Logger
import tech.units.indriya.ComparableQuantity

import javax.measure.quantity.Power
import scala.collection.generic.DefaultSerializable
import scala.collection.immutable.{AbstractSeq, StrictOptimizedSeqOps}
import scala.util.{Failure, Success, Try}

trait LoadCalculation {

  /** Determine the locations and the estimated consumed power of loads
    *
    * @param osmContainer
    *   Container with all available osm entities
    * @param loadDensity
    *   Load density per square metre
    * @param logger
    *   Logger to document the results
    * @return
    *   A [[List]] of [[LoadLocation]]s
    */
  protected def determineLoadLocations(
      osmContainer: OsmModel,
      loadDensity: ComparableQuantity[PowerDensity]
  )(implicit logger: Logger): Seq[LoadLocation[_]] = {
    val results =
      loadLocationsFromOsmContainer(osmContainer, loadDensity).groupBy(
        _._2.isSuccess
      )

    /* Report on failed entities */
    results.get(false).map(_.map(_._1)) match {
      case Some(failedEntityIds) =>
        logger.warn(
          s"Determination of load locations failed for the following ${failedEntityIds.length} entities:\n\t${failedEntityIds
            .mkString("\n\t")}"
        )
      case None =>
        logger.debug("No error during determination of load locations.")
    }

    /* Extract the valid results */
    results
      .get(true)
      .map {
        _.map {
          case (_, Success(loadLocation)) => loadLocation
          case (id, Failure(_)) =>
            throw RuntimeException(
              "Filtering of successful load location determination obviously went wrong."
            )
        }
      }
      .getOrElse(List.empty[LoadLocation[_]])
  }

  private def loadLocationsFromOsmContainer(
      osmContainer: OsmModel,
      loadDensity: ComparableQuantity[PowerDensity]
  ) = {
    OsmModel
      .extractBuildings(osmContainer.ways)
      .map(way => way.osmId -> loadFromWay(way, loadDensity))
  }

  /** Determine the load of a building, if it is modeled as a way
    *
    * @param way
    *   Way, describing the building
    * @param loadDensity
    *   Assumed load density
    * @return
    */
  private def loadFromWay(
      way: Way,
      loadDensity: ComparableQuantity[PowerDensity]
  ): Try[LoadLocation[Way]] = way match {
    case closedWay @ OsmEntities.ClosedWay(
          uuid,
          osmId,
          lastEdited,
          tags,
          nodes
        ) =>
      closedWay.area.map { area =>
        val load = loadDensity.multiply(area).asType(classOf[Power])
        LoadLocation(closedWay.center, load, closedWay)
      }
    case OsmEntities.OpenWay(_, osmId, _, _, _) =>
      Failure(
        IllegalCalculationException(
          s"Cannot determine the area of a building with id '$osmId' from an open way."
        )
      )
  }
}
