package edu.ie3.osmogrid.poi

import edu.ie3.osmogrid.ActorStopSupportStateless
import edu.ie3.osmogrid.io.input.FilterType.POI
import edu.ie3.osmogrid.io.input.{InputDataEvent, InputResponse, RepOsm, ReqOsm}
import edu.ie3.osmogrid.io.output.{PoiResult, ResultListenerProtocol}
import edu.ie3.osmogrid.model.OsmoGridModel
import edu.ie3.osmogrid.model.OsmoGridModel.{EnhancedOsmEntity, PoiModel}
import edu.ie3.util.geo.RichGeometries.calcAreaOnEarth
import edu.ie3.util.osm.model.OsmEntity
import edu.ie3.util.osm.model.OsmEntity.Way.{ClosedWay, OpenWay}
import edu.ie3.util.osm.model.OsmEntity.{Node, Relation}
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.slf4j.Logger
import tech.units.indriya.unit.Units
import utils.OsmoGridUtils.safeBuildPolygon

import scala.collection.parallel.ParSeq

object PoiParser extends ActorStopSupportStateless {

  sealed trait PoiRequest

  object StartParsing extends PoiRequest

  def apply(resultListener: ActorRef[ResultListenerProtocol])(using inputDataProvider: ActorRef[InputDataEvent]): Behavior[PoiRequest | InputResponse] = Behaviors.receive {
    case (ctx, StartParsing) =>
      ctx.log.info(s"Requesting points of interest ...")

      inputDataProvider ! ReqOsm(ctx.self, POI)
      Behaviors.same

    case (ctx, RepOsm(osmModel)) =>
      osmModel match {
        case poiModel: PoiModel =>
          ctx.log.info(s"Received osm entities. Try to parse them ...")
          val elements = parseElements(poiModel)(using ctx.log)

          resultListener ! PoiResult(elements)

          stopBehavior

        case other =>
          ctx.log.error(s"Cannot parse POIs from ${other.getClass}.")
          Behaviors.stopped
      }
  }

  def parseElements(poiModel: PoiModel)(using log: Logger): Iterable[PoiElement] =
    poiModel.asMap.flatMap { case (poiType, entities) => parseElements(poiType, entities) }

  private def parseElements(poiType: String, elements: ParSeq[EnhancedOsmEntity])(using log: Logger): Iterable[PoiElement] = {
    val (nodes, others) = elements.partition {
      _.entity match {
        case _: Node => true
        case _ => false
      }
    }

    val parsed = others.flatMap { enhancedEntity =>
      val subNodes = enhancedEntity.subEntities.flatMap { case (id, entity) =>
        entity match {
          case node: Node =>
            Some(id -> node)
          case _ => None
        }
      }

      enhancedEntity.entity match {
        case closedWay: ClosedWay =>
          val buildingPolygon = safeBuildPolygon(closedWay, subNodes)
          val point = buildingPolygon.getCentroid.getCoordinate

          val (lat, lon, area) = (point.y, point.x, buildingPolygon.calcAreaOnEarth)

          Some(PoiElement(enhancedEntity.entity.id, poiType, lat, lon, area.to(Units.SQUARE_METRE).getValue.doubleValue()))

        case openWay: OpenWay =>
          log.info(s"Cannot parse open way: $openWay")

          None

        case relation: Relation =>
          log.info(s"Cannot parse relation: $relation")

          None
      }
    }

    val averageSize = parsed.map(_.size).sum / parsed.size

    val parsedNodes = nodes.map { enhancedEntity =>
      enhancedEntity.entity match {
        case Node(id, latitude, longitude, tags, metaInformation) =>
          PoiElement(id, poiType, latitude, longitude, averageSize)
      }
    }

    (parsed ++ parsedNodes).seq
  }

  override protected def cleanUp(): Unit = {
    /* Nothing to do here. At least until now. */
  }
}
