/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.actor.typed.scaladsl.{Behaviors, Routers}
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.cfg.OsmoGridConfig.Generation.Lv
import edu.ie3.osmogrid.guardian.OsmoGridGuardian.{
  OsmoGridGuardianEvent,
  RepLvGrids
}
import edu.ie3.osmogrid.lv.LvGenerator
import edu.ie3.util.osm.OsmEntities.{OpenWay, RelationElement, Way}
import edu.ie3.util.osm.{OsmEntities, OsmModel}
import org.locationtech.jts.geom.impl.CoordinateArraySequence
import org.locationtech.jts.geom.{
  Coordinate,
  GeometryFactory,
  LinearRing,
  Polygon,
  PrecisionModel
}

object LvCoordinator {
  sealed trait LvCoordinatorEvent
  final case class ReqLvGrids(
      cfg: OsmoGridConfig.Generation.Lv,
      osmModel: OsmModel,
      replyTo: ActorRef[OsmoGridGuardianEvent]
  ) extends LvCoordinatorEvent

  // TODO replace with geoutils
  private val DEFAULT_GEOMETRY_FACTORY: GeometryFactory =
    new GeometryFactory(new PrecisionModel(), 4326)

  def apply(): Behavior[LvCoordinatorEvent] = idle

  private def idle: Behavior[LvCoordinatorEvent] = Behaviors.receive {
    (ctx, msg) =>
      msg match {
        case ReqLvGrids(
              Lv(
                amountOfGridGenerators,
                amountOfRegionCoordinators,
                distinctHouseConnections
              ),
              osmModel,
              replyTo
            ) =>
          ctx.log.info("Starting generation of low voltage grids!")
          /* TODO:
              1) Ask for OSM data
              2) Ask for asset data
              3) Split up osm data at municipality boundaries
              4) start generation */

          val boundaries = extractBoundaries(osmModel).map { r =>
            // combine all ways of a boundary relation to one sequence of coordinates
            val coordinates = r.elements
              .flatMap { case RelationElement(element, "outer") =>
                element match {
                  case way: Way =>
                    way.nodes
                  case _ => List.empty
                }
              }
              .map { el =>
                el.coordinates.getCoordinate
              }
              .toArray

            buildPolygon(coordinates)
          }

          // TODO node can now be partitioned using polygon.covers(node)

          /* Spawn a pool of workers to build grids from sub-graphs */
          val lvGeneratorPool =
            Routers.pool(poolSize = amountOfGridGenerators) {
              // Restart workers on failure
              Behaviors
                .supervise(LvGenerator())
                .onFailure(SupervisorStrategy.restart)
            }
          val lvGeneratorProxy = ctx.spawn(lvGeneratorPool, "LvGeneratorPool")

          /* Spawn a pool of workers to build grids for one municipality */
          val lvRegionCoordinatorPool =
            Routers.pool(poolSize = amountOfRegionCoordinators) {
              // Restart workers on failure
              Behaviors
                .supervise(LvRegionCoordinator(lvGeneratorProxy))
                .onFailure(SupervisorStrategy.restart)
            }
          val lvRegionCoordinatorProxy =
            ctx.spawn(lvRegionCoordinatorPool, "LvRegionCoordinatorPool")

          replyTo ! RepLvGrids(Vector.empty[SubGridContainer])
          Behaviors.stopped
        case unsupported =>
          ctx.log.error(s"Received unsupported message: $unsupported")
          Behaviors.stopped
      }
  }

  // TODO replace with convenience function in PowerSystemUtils
  private def buildPolygon(coordinates: Array[Coordinate]): Polygon = {
    val arrayCoordinates = new CoordinateArraySequence(coordinates)
    val linearRing =
      new LinearRing(arrayCoordinates, DEFAULT_GEOMETRY_FACTORY)
    new Polygon(linearRing, Array[LinearRing](), DEFAULT_GEOMETRY_FACTORY)
  }

  /** Returns a list of boundary relations consisting of counties ("Gemeinden")
    * and independent towns ("Kreisfreie Städte").
    *
    * Should work in most places in Germany.
    * @param osmModel
    *   the OSM model
    * @return
    *   list of boundary relations
    */
  private def extractBoundaries(
      osmModel: OsmModel
  ): List[OsmEntities.Relation] = {
    val relations = osmModel.relations.getOrElse(throw new RuntimeException())

    val boundaries = relations.filter {
      _.tags.get("boundary").contains("administrative")
    }

    val municipalities =
      boundaries.filter(_.tags.get("admin_level").contains("8"))

    // independent towns with tag de:place=city https://forum.openstreetmap.org/viewtopic.php?id=21788
    val independentCities = boundaries.filter { b =>
      b.tags.get("admin_level").contains("6") && b.tags
        .get("de:place")
        .contains("city")
    }

    municipalities.appendedAll(independentCities)
  }
}
