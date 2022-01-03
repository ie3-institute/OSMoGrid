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
import edu.ie3.osmogrid.guardian.OsmoGridGuardian
import edu.ie3.osmogrid.guardian.OsmoGridGuardian.{
  OsmoGridGuardianEvent,
  RepLvGrids
}
import edu.ie3.osmogrid.lv.LvGenerator
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
  final case class RepLvGrids(grids: Vector[SubGridContainer])
      extends LvCoordinatorEvent

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
              guardian
            ) =>
          ctx.log.info("Starting generation of low voltage grids!")
          /* TODO:
              1) Ask for OSM data
              2) Ask for asset data
              3) Split up osm data at municipality boundaries
              4) start generation */

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

          val boundaries = buildBoundaryPolygons(osmModel)

          def filterWay(polygon: Polygon, way: OsmEntities.Way) = {
            val majority = (way.nodes.size + 1) / 2
            way.nodes
              .filter { node =>
                polygon.covers(node.coordinates)
              }
              .sizeCompare(majority) > 0
          }

          def filterRelation(
              polygon: Polygon,
              relation: OsmEntities.Relation
          ): Boolean = {
            val majority = (relation.elements.size + 1) / 2
            relation.elements
              .map(_.element)
              .filter {
                case node: OsmEntities.Node =>
                  polygon.covers(node.coordinates)
                case way: OsmEntities.Way =>
                  filterWay(polygon, way)
                case relation: OsmEntities.Relation =>
                  filterRelation(polygon, relation)
              }
              .sizeCompare(majority) > 0
          }

          // TODO this scala-esk way might be inefficient, find better way
          boundaries.foreach { polygon =>
            val nodes = osmModel.nodes.filter { node =>
              polygon.covers(node.coordinates)
            }
            val ways = osmModel.ways.filter { way =>
              filterWay(polygon, way)
            }
            val relations = osmModel.relations.map {
              _.filter { relation =>
                filterRelation(polygon, relation)
              }
            }

            val model = OsmModel(nodes, ways, relations, polygon)

            lvRegionCoordinatorProxy ! LvRegionCoordinator.ReqLvGrids(
              model,
              ctx.self
            )
          }

          /* Wait for the incoming data and check, if all replies are received. */
          awaitReplies(0, guardian)
        case unsupported =>
          ctx.log.error(s"Received unsupported message: $unsupported")
          Behaviors.stopped
      }
  }

  private def awaitReplies(
      awaitedReplies: Int,
      guardian: ActorRef[OsmoGridGuardianEvent],
      collectedGrids: Vector[SubGridContainer] = Vector.empty
  ): Behaviors.Receive[LvCoordinatorEvent] = Behaviors.receive {
    case (ctx, RepLvGrids(grids)) =>
      val stillAwaited = awaitedReplies - 1
      ctx.log.debug(
        s"Received another ${grids.length} sub grids. ${if (stillAwaited == 0) "All requests are answered."
        else s"Still awaiting $stillAwaited replies."}."
      )
      val updatedGrids = collectedGrids ++ grids
      if (stillAwaited == 0) {
        ctx.log.info(
          s"Received ${updatedGrids.length} sub grid containers in total. Join and send them to the guardian."
        )
        guardian ! OsmoGridGuardian.RepLvGrids(updatedGrids)
        Behaviors.stopped
      } else
        awaitReplies(stillAwaited, guardian, updatedGrids)
    case (ctx, unsupported) =>
      ctx.log.error(s"Received unsupported message: $unsupported")
      Behaviors.stopped
  }

  private def buildBoundaryPolygons(osmModel: OsmModel) = {
    extractBoundaries(osmModel).map { r =>
      // combine all ways of a boundary relation to one sequence of coordinates
      val coordinates = r.elements
        .flatMap { case OsmEntities.RelationElement(element, "outer") =>
          element match {
            case way: OsmEntities.Way =>
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

  // TODO replace with convenience function in PowerSystemUtils
  private def buildPolygon(coordinates: Array[Coordinate]): Polygon = {
    val arrayCoordinates = new CoordinateArraySequence(coordinates)
    val linearRing =
      new LinearRing(arrayCoordinates, DEFAULT_GEOMETRY_FACTORY)
    new Polygon(linearRing, Array[LinearRing](), DEFAULT_GEOMETRY_FACTORY)
  }
}
