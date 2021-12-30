/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.io.input.pbf

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, PostStop}
import com.acervera.osm4scala.EntityIterator.fromBlob
import com.acervera.osm4scala.model.{NodeEntity, RelationEntity, WayEntity}
import edu.ie3.osmogrid.io.input.pbf.PbfGuardian.stopAndCleanup
import edu.ie3.util.geo.GeoUtils
import edu.ie3.util.osm.OsmEntities.{ClosedWay, Node, OpenWay, Relation, Way}
import edu.ie3.util.osm.OsmModel
import org.openstreetmap.osmosis.osmbinary.fileformat.{Blob, BlobHeader}

import java.time.ZonedDateTime
import java.util.UUID

private[pbf] object PbfWorker {

  // external request protocol
  sealed trait PbfWorkerMsg
  final case class ReadBlobMsg(
      blobHeader: BlobHeader,
      blob: Blob,
      replyTo: ActorRef[PbfWorker.Response]
  ) extends PbfWorkerMsg

  // external response protocol
  sealed trait Response
  final case class ReadSuccessful(
      nodes: List[Node],
      ways: List[Way],
      relations: List[Relation],
      highways: List[Way],
      buildings: List[Way],
      landuses: List[Way]
  ) extends Response
  final case class ReadFailed(
      blobHeader: BlobHeader,
      blob: Blob,
      exception: Throwable
  ) extends Response

  private type Nodes = List[Node]
  private type Ways = List[Way]
  private type Relations = List[Relation]
  private type Highways = List[Way]
  private type Buildings = List[Way]
  private type Landuses = List[Way]

  def apply(
      highwayTags: Option[Set[String]] = None,
      buildingTags: Option[Set[String]] = None,
      landuseTags: Option[Set[String]] = None
  ): Behavior[PbfWorkerMsg] = Behaviors.receiveMessage[PbfWorkerMsg] {
    case ReadBlobMsg(_, blob, replyTo) =>
      val (nodes, ways, relations) = readBlob(blob)
      val (highways, buildings, landuses) = (
        OsmModel.extractHighways(ways, highwayTags),
        OsmModel.extractBuildings(ways, buildingTags),
        OsmModel.extractLandUses(ways, landuseTags)
      )
      replyTo ! ReadSuccessful(
        nodes,
        ways,
        relations,
        highways,
        buildings,
        landuses
      )
      Behaviors.same
  }

  private def readBlob(blob: Blob): (Nodes, Ways, Relations) = {
    fromBlob(blob).foldLeft(
      (List.empty[Node], List.empty[Way], List.empty[Relation])
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
  }

  private def osmNode(nodeEntity: NodeEntity) =
    Node(
      null, // todo kill
      nodeEntity.id.toInt, // todo make Long
      ZonedDateTime.now(),
      nodeEntity.tags,
      GeoUtils.DEFAULT_GEOMETRY_FACTORY
        .createPoint(
          org.locationtech.jts.geom
            .Coordinate(nodeEntity.longitude, nodeEntity.latitude)
        )
    )

  private def osmWay(wayEntity: WayEntity) =
    if (isClosedWay(wayEntity)) {
      ClosedWay(
        null, // todo kill
        wayEntity.id.toInt, // todo make Long
        ZonedDateTime.now(),
        wayEntity.tags,
        null // todo replace with node Ids (Long)
      )
    } else {
      OpenWay(
        null, // todo kill
        wayEntity.id.toInt, // todo make Long
        ZonedDateTime.now(),
        wayEntity.tags,
        null // todo replace with node Ids (Long)
      )
    }

  private def osmRelation(relationEntity: RelationEntity) =
    Relation(
      null, // todo kill
      relationEntity.id.toInt, // todo make Long
      ZonedDateTime.now(),
      relationEntity.tags,
      null // todo fix
    )

  private def isClosedWay(wayEntity: WayEntity): Boolean = {
    val nodes = wayEntity.nodes
    nodes.headOption.zip(nodes.lastOption).exists { case (head, last) =>
      head == last
    }
  }
}
