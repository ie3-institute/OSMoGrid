/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.Behaviors
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.io.input.BoundaryAdminLevel
import edu.ie3.osmogrid.model.OsmoGridModel.{EnhancedOsmEntity, LvOsmoGridModel}
import edu.ie3.util.geo.GeoUtils
import edu.ie3.util.osm.model.OsmEntity.Relation.RelationMemberType
import edu.ie3.util.osm.model.OsmEntity
import org.locationtech.jts.geom.Polygon

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
          val boundaries =
            buildBoundaryPolygons(osmoGridModel, administrativeLevel)

          def filterNode(
              polygon: Polygon,
              node: OsmEntity.Node,
              enhancedEntity: EnhancedOsmEntity[_]
          ) = {
            val point = GeoUtils.buildPoint(node.latitude, node.longitude)
            polygon.covers(point)
          }

          def filterWay(
              polygon: Polygon,
              way: OsmEntity.Way,
              enhancedEntity: EnhancedOsmEntity[_]
          ) = {
            val majority = (way.nodes.size + 1) / 2
            way.nodes
              .flatMap(enhancedEntity.node)
              .filter { node =>
                filterNode(polygon, node, enhancedEntity)
              }
              .sizeCompare(majority) > 0
          }

          def filterRelation(
              polygon: Polygon,
              relation: OsmEntity.Relation,
              enhancedEntity: EnhancedOsmEntity[_]
          ): Boolean = {
            val majority = (relation.members.size + 1) / 2
            relation.members
              .flatMap { member =>
                member.relationType match {
                  case RelationMemberType.Node =>
                    enhancedEntity.node(member.id)
                  case RelationMemberType.Way =>
                    enhancedEntity.way(member.id)
                  case RelationMemberType.Relation =>
                    enhancedEntity.relation(member.id)
                  case RelationMemberType.Unrecognized =>
                    None
                }
              }
              .filter(entity => filterEntity(polygon, entity, enhancedEntity))
              .sizeCompare(majority) > 0
          }

          def filterEntity(
              polygon: Polygon,
              entity: OsmEntity,
              enhancedEntity: EnhancedOsmEntity[_]
          ): Boolean = {
            entity match {
              case node: OsmEntity.Node =>
                filterNode(polygon, node, enhancedEntity)
              case way: OsmEntity.Way =>
                filterWay(polygon, way, enhancedEntity)
              case relation: OsmEntity.Relation =>
                filterRelation(polygon, relation, enhancedEntity)
            }
          }

          val newOsmoGridModels =
            if boundaries.isEmpty then
              // if no containers have been found at this level, we continue with container of previous level
              Iterable.single(osmoGridModel)
            else
              boundaries.map { polygon =>
                val buildings = osmoGridModel.buildings.filter {
                  enhancedEntity =>
                    filterEntity(polygon, enhancedEntity.entity, enhancedEntity)
                }
                val highways = osmoGridModel.highways.filter { enhancedEntity =>
                  filterEntity(polygon, enhancedEntity.entity, enhancedEntity)
                }
                val landuses = osmoGridModel.landuses.filter { enhancedEntity =>
                  filterEntity(polygon, enhancedEntity.entity, enhancedEntity)
                }
                val boundaries = osmoGridModel.boundaries.filter {
                  enhancedEntity =>
                    filterEntity(polygon, enhancedEntity.entity, enhancedEntity)
                }
                val existingSubstations =
                  osmoGridModel.existingSubstations.filter { enhancedEntity =>
                    filterEntity(polygon, enhancedEntity.entity, enhancedEntity)
                  }

                LvOsmoGridModel(
                  buildings,
                  highways,
                  landuses,
                  boundaries,
                  existingSubstations,
                  osmoGridModel.filter
                )
              }.iterator

          newOsmoGridModels.foreach { osmoGridModel =>
            // boundaries in Germany: https://wiki.openstreetmap.org/wiki/DE:Grenze#Innerstaatliche_Grenzen

            BoundaryAdminLevel(cfg.boundaryAdminLevel.lowest)
              .zip(BoundaryAdminLevel.nextLowerLevel(administrativeLevel))
              .filter((lowestLevel, nextLevel) =>
                nextLevel <= lowestLevel
              ) match {
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

  private def buildBoundaryPolygons(
      osmoGridModel: LvOsmoGridModel,
      administrativeLevel: BoundaryAdminLevel
  ) = {
    osmoGridModel.boundaries.flatMap {
      case enhancedRelation @ EnhancedOsmEntity(
            relation: OsmEntity.Relation,
            subEntities
          )
          if relation.tags
            .get("admin_level")
            .contains(administrativeLevel.osmLevel.toString) =>
        // combine all ways of a boundary relation to one sequence of coordinates
        val coordinates = relation.members
          .flatMap {
            case OsmEntity.Relation.RelationMember(id, relationType, role) =>
              relationType match {
                case RelationMemberType.Way =>
                  enhancedRelation.way(id)
                case _ => None
              }
          }
          .flatMap { way =>
            enhancedRelation.nodes(way.nodes)
          }
          .flatten
          .map { node =>
            GeoUtils.buildCoordinate(node.latitude, node.longitude)
          }
          .toArray

        Some(GeoUtils.buildPolygon(coordinates))
      case _ => None
    }
  }

}
