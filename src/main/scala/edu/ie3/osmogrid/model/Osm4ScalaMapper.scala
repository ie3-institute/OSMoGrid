/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.model

import com.acervera.osm4scala.model.RelationMemberEntityTypes.RelationMemberEntityTypes
import com.acervera.osm4scala.model.{
  Info,
  NodeEntity,
  RelationEntity,
  RelationMemberEntity,
  WayEntity
}
import edu.ie3.util.osm.model.OsmEntity.Relation.{
  RelationMember,
  RelationMemberType
}
import edu.ie3.util.osm.model.OsmEntity.{MetaInformation, Node, Relation, Way}

object Osm4ScalaMapper {

  def osmNode(nodeEntity: NodeEntity): Node =
    Node(
      nodeEntity.id,
      nodeEntity.latitude,
      nodeEntity.longitude,
      nodeEntity.tags,
      metaInformation(nodeEntity.info)
    )

  def osmWay(wayEntity: WayEntity): Way = Way(
    wayEntity.id,
    wayEntity.nodes,
    wayEntity.tags,
    metaInformation(wayEntity.info)
  )

  def metaInformation(info: Option[Info]): Option[MetaInformation] =
    info.map {
      case Info(version, timestamp, changeset, userId, userName, visible) =>
        MetaInformation(
          version,
          timestamp,
          changeset,
          userId,
          userName,
          visible
        )
    }

  def osmRelation(relationEntity: RelationEntity): Relation =
    Relation(
      relationEntity.id,
      relationMember(relationEntity.relations),
      relationEntity.tags,
      metaInformation(relationEntity.info)
    )

  def relationMember(
      relationMemberEntities: Seq[RelationMemberEntity]
  ): Seq[RelationMember] =
    relationMemberEntities.map(relationMemberEntity =>
      RelationMember(
        relationMemberEntity.id,
        relationType(relationMemberEntity.relationTypes),
        relationMemberEntity.role
      )
    )

  def relationType(
      relationTypes: RelationMemberEntityTypes
  ): RelationMemberType = relationTypes match {
    case com.acervera.osm4scala.model.RelationMemberEntityTypes.Node =>
      RelationMemberType.Node
    case com.acervera.osm4scala.model.RelationMemberEntityTypes.Way =>
      RelationMemberType.Way
    case com.acervera.osm4scala.model.RelationMemberEntityTypes.Relation =>
      RelationMemberType.Relation
    case com.acervera.osm4scala.model.RelationMemberEntityTypes.Unrecognized =>
      RelationMemberType.Unrecognized
  }

}
