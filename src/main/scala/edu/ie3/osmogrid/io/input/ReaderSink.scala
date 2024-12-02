/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.io.input

import edu.ie3.osmogrid.exception.PbfReadFailedException
import edu.ie3.osmogrid.model.OsmoGridModel.LvOsmoGridModel
import edu.ie3.osmogrid.model.{OsmoGridModel, SourceFilter}
import edu.ie3.util.osm.model.OsmContainer.ParOsmContainer
import edu.ie3.util.osm.model.OsmEntity.Relation.RelationMemberType
import edu.ie3.util.osm.model.{OsmEntity => UtilsEntity}
import org.apache.pekko.actor.typed.ActorRef
import org.openstreetmap.osmosis.core.container.v0_6.EntityContainer
import org.openstreetmap.osmosis.core.domain.v0_6._
import org.openstreetmap.osmosis.core.task.v0_6.Sink
import org.slf4j.Logger

import java.io.FileInputStream
import java.util
import scala.collection.parallel.CollectionConverters._
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

/** A [[Sink]] that will process the read data.
  * @param inputStream
  *   to close after finishing the processing
  * @param filter
  *   for the data
  * @param requester
  *   to send a reply to
  * @param log
  *   for logging purposes
  * @param nodes
  *   list of processed nodes
  * @param ways
  *   list of processed ways
  * @param relations
  *   list of processed relations
  */
case class ReaderSink(
    inputStream: FileInputStream,
    filter: SourceFilter,
    requester: ActorRef[InputDataEvent],
    log: Logger,
    private var nodes: util.List[UtilsEntity.Node] = new util.ArrayList(),
    private var ways: util.List[UtilsEntity.Way] = new util.ArrayList(),
    private var relations: util.List[UtilsEntity.Relation] =
      new util.ArrayList(),
) extends Sink {
  override def process(entityContainer: EntityContainer): Unit = {

    entityContainer.getEntity match {
      case node: Node =>
        nodes.add(
          UtilsEntity.Node(
            node.getId,
            node.getLatitude,
            node.getLongitude,
            tags(node),
            buildMetaInfo(node),
          )
        )
      case relation: Relation =>
        val members = relation.getMembers.asScala
          .map { member =>
            val memberType = member.getMemberType match {
              case EntityType.Bound    => RelationMemberType.Unrecognized
              case EntityType.Node     => RelationMemberType.Node
              case EntityType.Way      => RelationMemberType.Way
              case EntityType.Relation => RelationMemberType.Relation
            }

            UtilsEntity.Relation.RelationMember(
              member.getMemberId,
              memberType,
              member.getMemberRole,
            )
          }

        relations.add(
          UtilsEntity.Relation(
            relation.getId,
            members.toSeq,
            tags(relation),
            buildMetaInfo(relation),
          )
        )
      case way: Way =>
        ways.add(
          UtilsEntity.Way(
            way.getId,
            way.getWayNodes.asScala.map(_.getNodeId).toSeq,
            tags = tags(way),
            buildMetaInfo(way),
          )
        )
      case unrecognized =>
        log.info(s"Unrecognized entity: $unrecognized")
    }
  }

  override def initialize(metaData: util.Map[String, AnyRef]): Unit = {
    // nothing to do here
  }

  override def complete(): Unit = {

    val osmoGridModel = Try {
      val osmContainer = ParOsmContainer(
        nodes.asScala.toSeq.par,
        ways.asScala.toSeq.par,
        relations.asScala.toSeq.par,
      )

      filter match {
        case lvFilter: SourceFilter.LvFilter =>
          LvOsmoGridModel(osmContainer, lvFilter, filterNodes = false)
      }
    }

    osmoGridModel match {
      case Success(model: OsmoGridModel) =>
        requester ! RepOsm(model)
      case Failure(exception) =>
        requester ! OsmReadFailed(
          PbfReadFailedException(s"Reading failed due to: $exception")
        )
    }
  }

  override def close(): Unit = inputStream.close()

  private def tags(entity: Entity): Map[String, String] =
    entity.getTags.asScala.map(tag => tag.getKey -> tag.getValue).toMap

  private def buildMetaInfo(
      entity: Entity
  ): Option[UtilsEntity.MetaInformation] = {

    Option(
      UtilsEntity.MetaInformation(
        Option(entity.getVersion),
        Option(entity.getTimestamp.toInstant),
        Option(entity.getChangesetId),
        Option(entity.getUser.getId),
        Option(entity.getUser.getName),
        None,
      )
    )
  }

}
