/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.io.input.pbf

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, PostStop}
import com.acervera.osm4scala.EntityIterator.fromBlob
import com.acervera.osm4scala.model.RelationMemberEntityTypes.RelationMemberEntityTypes
import com.acervera.osm4scala.model.{
  Info,
  NodeEntity,
  RelationEntity,
  RelationMemberEntity,
  WayEntity
}
import edu.ie3.osmogrid.io.input.pbf.PbfGuardian.stopAndCleanup
import edu.ie3.osmogrid.model.Osm4ScalaMapper.{osmNode, osmRelation, osmWay}
import edu.ie3.osmogrid.model.OsmoGridModel.LvOsmoGridModel
import edu.ie3.osmogrid.model.{OsmoGridModel, PbfFilter}
import edu.ie3.util.geo.GeoUtils
import edu.ie3.util.osm.model.OsmContainer
import edu.ie3.util.osm.model.OsmContainer.ParOsmContainer
import edu.ie3.util.osm.model.OsmEntity.{MetaInformation, Node, Relation, Way}
import edu.ie3.util.osm.model.OsmEntity.Relation.{
  RelationMember,
  RelationMemberType
}
import org.openstreetmap.osmosis.osmbinary.fileformat.{Blob, BlobHeader}

import java.time.ZonedDateTime
import java.util.UUID
import scala.collection.parallel.immutable.ParSeq

private[pbf] object PbfWorker {

  // external request protocol
  sealed trait PbfWorkerMsg

  final case class ReadBlobMsg(
      blobHeader: BlobHeader,
      blob: Blob,
      filter: PbfFilter,
      replyTo: ActorRef[PbfWorker.Response]
  ) extends PbfWorkerMsg

  // external response protocol
  sealed trait Response

  final case class ReadSuccessful(osmoGridModel: OsmoGridModel) extends Response

  final case class ReadFailed(
      blobHeader: BlobHeader,
      blob: Blob,
      filter: PbfFilter,
      exception: Throwable
  ) extends Response

  def apply(
      highwayTags: Option[Set[String]] = None,
      buildingTags: Option[Set[String]] = None,
      landuseTags: Option[Set[String]] = None
  ): Behavior[PbfWorkerMsg] = Behaviors.receiveMessage[PbfWorkerMsg] {
    case ReadBlobMsg(_, blob, filter, replyTo) =>
      val osmContainer = readBlob(blob)

      val osmoGridModel = filter match {
        case lvFilter: PbfFilter.LvFilter =>
          LvOsmoGridModel(osmContainer, lvFilter, filterNodes = false)
      }

      replyTo ! ReadSuccessful(osmoGridModel)

      Behaviors.same
  }

  private def readBlob(blob: Blob): ParOsmContainer = {
    val (nodes, ways, relations) = fromBlob(blob).foldLeft(
      (ParSeq.empty[Node], ParSeq.empty[Way], ParSeq.empty[Relation])
    ) { case ((nodes, ways, relations), curOsmEntity) =>
      curOsmEntity match {
        case node: NodeEntity =>
          (nodes :+ osmNode(node), ways, relations)
        case way: WayEntity =>
          (nodes, ways :+ osmWay(way), relations)
        case relation: RelationEntity =>
          (nodes, ways, relations :+ osmRelation(relation))
      }
    }
    ParOsmContainer(nodes, ways, relations)
  }

}