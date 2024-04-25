/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.io.input

import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.cfg.OsmoGridConfig.Input.Osm
import edu.ie3.osmogrid.exception.IllegalConfigException
import edu.ie3.osmogrid.model.SourceFilter
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.scaladsl.ActorContext
import org.openstreetmap.osmosis.pbf2.v0_6.PbfReader

import java.io.{File, FileInputStream}

sealed trait OsmSource {

  def read(
      filter: SourceFilter,
      requester: ActorRef[InputDataEvent]
  ): Unit

  def close(): Unit

}

object OsmSource {

  final case class PbfFileSource(
      filePath: String,
      ctx: ActorContext[_]
  ) extends OsmSource {

    def read(
        filter: SourceFilter,
        requester: ActorRef[InputDataEvent]
    ): Unit = {
      val inputStream = new FileInputStream(new File(filePath))

      val sink = ReaderSink(inputStream, filter, requester, ctx.log)

      val reader =
        new PbfReader(
          () => inputStream,
          Runtime.getRuntime.availableProcessors()
        )
      reader.setSink(sink)
      reader.run()
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
