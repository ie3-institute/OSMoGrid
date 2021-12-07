/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.io.input

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.acervera.osm4scala.EntityIterator.fromPbf
import com.acervera.osm4scala.model.{NodeEntity, RelationEntity, WayEntity}
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.guardian.OsmoGridGuardian.OsmoGridGuardianEvent
import edu.ie3.osmogrid.io.input.InputDataProvider.readPbf
import edu.ie3.util.osm.OsmEntities.{Node, OpenWay, Relation, Way}
import edu.ie3.util.osm.OsmModel
import org.locationtech.jts.geom.{
  Coordinate,
  LinearRing,
  Point,
  Polygon,
  PrecisionModel
}

import java.io.{FileInputStream, InputStream}
import java.time.ZonedDateTime
import java.util.UUID
import scala.collection.mutable.ListBuffer

object InputDataProvider {

  sealed trait InputDataEvent
  final case class ReqOsm(replyTo: ActorRef[Response]) extends InputDataEvent
  final case class ReqAssetTypes(replyTo: ActorRef[Response])
      extends InputDataEvent

  sealed trait Response
  final case class RepOsm(osmModel: OsmModel) extends Response
  final case class RepAssetTypes(osmModel: OsmModel) extends Response

  final case class Terminate(replyTo: ActorRef[OsmoGridGuardianEvent])
      extends InputDataEvent

  def apply(cfgInput: OsmoGridConfig.Input): Behavior[InputDataEvent] =
    Behaviors.receive[InputDataEvent] {
      case (_, ReqOsm(replyTo)) =>
        replyTo ! RepOsm(readPbf(cfgInput.osm.pbf.get.file))
        Behaviors.same
      case (ctx, Terminate(_)) =>
        ctx.log.info("Stopping input data provider")
        // TODO: Any closing of sources and stuff
        Behaviors.stopped
    }

  def readPbf(importPath: String): OsmModel = {
    var pbfIS: InputStream = null
    var (nodes, ways, relations) =
      try {
        pbfIS = new FileInputStream(importPath)
        fromPbf(pbfIS)
          .foldLeft(
            (ListBuffer[Node](), ListBuffer[Way](), ListBuffer[Relation]())
          ) { case ((nodes, ways, relations), e) =>
            e match {
              case n: NodeEntity =>
                (
                  nodes.addOne(
                    Node(
                      UUID.randomUUID(),
                      n.id.toInt,
                      ZonedDateTime.now(),
                      n.tags,
                      Point(
                        Coordinate(n.latitude, n.longitude),
                        PrecisionModel(),
                        4326
                      )
                    )
                  ),
                  ways,
                  relations
                )
              case r: RelationEntity => (nodes, ways, relations)
              case w: WayEntity =>
                (
                  nodes,
                  ways.addOne(
                    OpenWay(
                      UUID.randomUUID(),
                      w.id.toInt,
                      ZonedDateTime.now(),
                      w.tags,
                      w.nodes
                        .foldLeft((ListBuffer[Node]())) {
                          case ((osmNodes), e) =>
                            osmNodes.addOne(
                              nodes.find(Node => Node.osmId == e.toInt).get
                            )
                        }
                        .toList
                    )
                  ),
                  relations
                )
              case _ => (nodes, ways, relations)
            }
          }
      } finally {
        if (pbfIS != null) pbfIS.close()
      }
    OsmModel(
      nodes.toList,
      ways.toList,
      Option(relations.toList),
      Polygon(
        LinearRing(
          Array(
            Coordinate(1000.0, 1000.0),
            Coordinate(1000.0, -1000.0),
            Coordinate(-1000.0, -1000.0),
            Coordinate(-1000.0, 1000.0),
            Coordinate(1000.0, 1000.0)
          ),
          PrecisionModel(),
          4326
        ),
        PrecisionModel(),
        4326
      )
    )
  }

}
