/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.io.input

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.ActorContext
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.cfg.OsmoGridConfig.Input.Osm
import edu.ie3.osmogrid.exception.IllegalConfigException
import edu.ie3.osmogrid.io.input.pbf.PbfGuardian
import edu.ie3.osmogrid.model.{OsmoGridModel, SourceFilter}

import java.io.File
import java.util.UUID
import scala.concurrent.{ExecutionContextExecutor, Future}

sealed trait OsmSource {

  def read(
      filter: SourceFilter
  ): Future[OsmoGridModel]

  def close(): Unit

}

object OsmSource {

  final case class PbfFileSource(
      filePath: String,
      ctx: ActorContext[_]
  ) extends OsmSource {

    def read(
        filter: SourceFilter
    ): Future[OsmoGridModel] = {

      val pbfReader = ctx.spawn(
        PbfGuardian.apply(new File(filePath), filter),
        name = s"pbf-reader-guardian-${UUID.randomUUID()}"
      )

      import akka.actor.typed.scaladsl.AskPattern._
      import akka.util.Timeout
      import concurrent.duration.DurationInt

      // 3 hours should be more than sufficient - even for very large files on small computers
      implicit val timeout: Timeout = 10800.seconds
      implicit val system: ActorSystem[_] = ctx.system
      implicit val ec: ExecutionContextExecutor = system.executionContext

      pbfReader.ask(sender => PbfGuardian.Run(sender)).flatMap {
        case PbfGuardian.PbfReadSuccessful(osmoGridModel) =>
          Future.successful(osmoGridModel)
        case PbfGuardian.PbfReadFailed(exception) =>
          Future.failed(exception)
      }
    }

    def close(): Unit = {
      // nothing to do here, all actors used in this source are already shutdown and the file source is closed
    }
  }

  def apply(
      osmCfg: OsmoGridConfig.Input.Osm,
      actorContext: ActorContext[InputDataEvent]
  ): OsmSource =
    getOsmSource(osmCfg).apply(actorContext)

  private def getOsmSource(
      osm: OsmoGridConfig.Input.Osm
  ): ActorContext[InputDataEvent] => OsmSource =
    osm match {
      case Osm(Some(pbf: OsmoGridConfig.Input.Osm.Pbf)) =>
        getPbfFileSource(pbf)
      case Osm(None) =>
        throw IllegalConfigException(
          "You have to provide at least one input data type for open street map information!"
        )
    }

  private def getPbfFileSource(
      pbf: OsmoGridConfig.Input.Osm.Pbf
  ): ActorContext[InputDataEvent] => PbfFileSource =
    pbf match {
      case OsmoGridConfig.Input.Osm.Pbf(file) if file.isEmpty =>
        throw IllegalConfigException(s"Pbf file '$file' not found!")
      case pbfFile =>
        (ctx: ActorContext[InputDataEvent]) => PbfFileSource(pbfFile.file, ctx)
    }

}
