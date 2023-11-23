/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.io.input.pbf

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import com.acervera.osm4scala.EntityIterator.fromBlob
import com.acervera.osm4scala.model.{NodeEntity, RelationEntity, WayEntity}
import edu.ie3.osmogrid.model.Osm4ScalaMapper.{osmNode, osmRelation, osmWay}
import edu.ie3.util.osm.model.OsmContainer.ParOsmContainer
import edu.ie3.util.osm.model.OsmEntity.{Node, Relation, Way}
import org.openstreetmap.osmosis.osmbinary.fileformat.Blob

import scala.collection.parallel.immutable.ParSeq

private[pbf] object PbfWorker {
  def apply(): Behavior[PbfWorkerRequest] =
    Behaviors.receiveMessage[PbfWorkerRequest] {
      case ReadBlobMsg(_, blob, replyTo) =>
        val osmContainer = readBlob(blob)

        replyTo ! ReadSuccessful(osmContainer)

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
