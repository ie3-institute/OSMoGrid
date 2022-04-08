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

import scala.collection.parallel.ParSeq

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
              enhancedEntity: EnhancedOsmEntity
          ) = {
            val point = GeoUtils.buildPoint(node.latitude, node.longitude)
            polygon.covers(point)
          }

          def filterWay(
              polygon: Polygon,
              way: OsmEntity.Way,
              enhancedEntity: EnhancedOsmEntity
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
              enhancedEntity: EnhancedOsmEntity
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
              enhancedEntity: EnhancedOsmEntity
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

  /** Creating boundary polygons from corresponding relations at given
    * administrative level
    *
    * @param osmoGridModel
    *   the data model to take boundaries from
    * @param administrativeLevel
    *   the administrative level for which boundary polygons should be created
    * @return
    *   the boundary polygons
    */
  private def buildBoundaryPolygons(
      osmoGridModel: LvOsmoGridModel,
      administrativeLevel: BoundaryAdminLevel
  ): ParSeq[Polygon] = {
    osmoGridModel.boundaries
      .filter {
        case EnhancedOsmEntity(
              entity: OsmEntity,
              subEntities
            ) =>
          entity match {
            case relation: OsmEntity.Relation =>
              relation.tags
                .get("admin_level")
                .contains(administrativeLevel.osmLevel.toString)
            case _ => false
          }
      }
      .map(buildBoundaryPolygon)
  }

  /** Creates a boundary polygon for given boundary relation.
    *
    * Boundary relations consist of one or more ways, of which two consecutive
    * ways share the first and last node. The nodes in ways can be ordered in
    * both directions, thus all permutations have to be checked.
    *
    * @param enhancedRelation
    *   the relation wrapped in an [[EnhancedOsmEntity]]
    * @return
    *   the boundary polygon
    */
  private def buildBoundaryPolygon(
      enhancedRelation: EnhancedOsmEntity
  ): Polygon = {
    val relation = enhancedRelation.entity match {
      case relation: OsmEntity.Relation => relation
      case other =>
        throw new RuntimeException(
          s"Wrong entity type ${other.getClass}, Way is required"
        )
    }

    val coordinates = (relation.members
      .flatMap {
        // Filter for ways only
        case OsmEntity.Relation.RelationMember(id, relationType, role) =>
          relationType match {
            case RelationMemberType.Way =>
              enhancedRelation.way(id)
            case _ =>
              // We only want ways, discard all other types
              None
          }
      }
      .foldLeft(Seq.empty[Long]) { case (allNodes, currentWay) =>
        // Construct one single sequence of nodes by joining the ways.
        // Each way can be ordered in correct or in reverse order
        val currentNodes = currentWay.nodes
        allNodes.headOption.zip(allNodes.lastOption) match {
          case Some(existingFirst, existingLast) =>
            currentNodes.headOption
              .zip(currentNodes.lastOption)
              .map { case (currentFirst, currentLast) =>
                // Run through a bunch of cases. In the end, we want [a, b, c, d, e]
                if existingLast == currentFirst then
                  // [a, b, c] and [c, d, e]
                  allNodes ++ currentNodes.drop(1)
                else if existingLast == currentLast then
                  // [a, b, c] and [e, d, c]
                  allNodes ++ currentNodes.reverse.drop(1)
                else if existingFirst == currentFirst then
                  // [c, b, a] and [c, d, e]
                  // Existing sequence in wrong order:
                  // this should only happen if we have only added one
                  // sequence yet and that sequence was in wrong order
                  allNodes.reverse ++ currentNodes.drop(1)
                else if existingFirst == currentLast then
                  // [c, b, a] and [e, d, c]; a != e since we already covered this in first condition
                  // Existing sequence in wrong order, similar to above
                  // but additional sequence has be flipped as well
                  allNodes.reverse ++ currentNodes.reverse.drop(1)
                else
                  throw new RuntimeException(
                    s"Could not create Polygon from relation ${relation.id}: Last node $existingLast was not found in way ${currentWay.id}"
                  )
              }
              .getOrElse(
                // Current way is empty, carry on with old sequence
                allNodes
              )
          case None =>
            //  No nodes added yet, just put current ones in place
            currentNodes
        }
      } match {
      // Sanity check: in the end, first node should equal the last node
      case nodes
          if nodes.headOption
            .zip(nodes.lastOption)
            .exists((first, last) => first != last) =>
        throw new RuntimeException(
          s"First node should be last in boundary relation ${relation.id}."
        )
      case nodes if nodes.isEmpty =>
        throw new RuntimeException(
          s"Empty boundary relation ${relation.id}."
        )
      case nodes => nodes
    })
      .map { node =>
        // Turn node ids into nodes
        enhancedRelation
          .node(node)
          .getOrElse(
            throw new RuntimeException(
              s"Node $node not found in enhanced relation $enhancedRelation"
            )
          )
      }
      .map { node =>
        // Turn nodes into coordinates
        GeoUtils.buildCoordinate(node.latitude, node.longitude)
      }
      .toArray

    GeoUtils.buildPolygon(coordinates)
  }

}
