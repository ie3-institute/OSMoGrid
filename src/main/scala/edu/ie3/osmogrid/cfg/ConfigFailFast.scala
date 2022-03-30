/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.cfg

import akka.actor.typed.ActorRef
import com.typesafe.scalalogging.LazyLogging
import edu.ie3.osmogrid.cfg.OsmoGridConfig.{Input, Output}
import edu.ie3.osmogrid.cfg.OsmoGridConfig.Input.{Asset, Osm}
import edu.ie3.osmogrid.cfg.OsmoGridConfig.Generation
import edu.ie3.osmogrid.cfg.OsmoGridConfig.Generation.Lv
import edu.ie3.osmogrid.cfg.OsmoGridConfig.Input.Asset.File
import edu.ie3.osmogrid.exception.IllegalConfigException
import edu.ie3.osmogrid.io.input.BoundaryAdminLevel
import edu.ie3.osmogrid.io.output.ResultListener

import scala.util.Try

object ConfigFailFast extends LazyLogging {
  def check(
      cfg: OsmoGridConfig,
      additionalListener: Seq[ActorRef[ResultListener.ResultEvent]] = Seq.empty
  ): Try[OsmoGridConfig] = Try {
    cfg match {
      case OsmoGridConfig(generation, input, output) =>
        checkInputConfig(input)
        checkOutputConfig(output, additionalListener)
        checkGenerationConfig(generation)
    }
    cfg
  }

  private def checkGenerationConfig(generation: Generation): Unit =
    generation match {
      case Generation(lv) =>
        /* Check, that at least one config is set */
        if (Vector(lv).count(_.isDefined) < 1)
          throw IllegalConfigException(
            "At least one voltage level generation config has to be defined."
          )

        /* Check single configs */
        lv.foreach(checkLvConfig)
    }

  private def checkLvConfig(lv: OsmoGridConfig.Generation.Lv): Unit = lv match {
    case Lv(
          Lv.BoundaryAdminLevel(
            lowest,
            starting
          ),
          _,
          _
        ) =>
      (BoundaryAdminLevel(lowest), BoundaryAdminLevel(starting)) match {
        case (None, _) =>
          throw IllegalConfigException(
            s"The lowest admin level can not be parsed (provided: $lowest)."
          )
        case (_, None) =>
          throw IllegalConfigException(
            s"The starting admin level can not be parsed (provided: $starting)."
          )
        case (Some(lowestParsed), Some(startingParsed))
            if startingParsed > lowestParsed =>
          throw IllegalConfigException(
            s"The starting admin level (provided: $startingParsed) has to be higher than the lowest admin level (provided: $lowestParsed)."
          )
        case _ =>
        // all good, do nothing
      }
  }

  private def checkInputConfig(input: OsmoGridConfig.Input): Unit =
    input match {
      case Input(asset, osm) =>
        checkAssetInputConfig(asset)
        checkOsmInputConfig(osm)
    }

  private def checkAssetInputConfig(asset: OsmoGridConfig.Input.Asset): Unit =
    asset match {
      case Asset(Some(file)) => checkAssetInputFile(file)
      case Asset(None) =>
        throw IllegalConfigException(
          "You have to provide at least one input data type for asset information!"
        )
    }

  private def checkAssetInputFile(file: OsmoGridConfig.Input.Asset.File): Unit =
    if (file.directory.isEmpty)
      throw IllegalConfigException("Asset input directory may be set!")

  private def checkOsmInputConfig(osm: OsmoGridConfig.Input.Osm): Unit =
    osm match {
      case Osm(Some(file)) => checkPbfFileDefinition(file)
      case Osm(None) =>
        throw IllegalConfigException(
          "You have to provide at least one input data type for open street map information!"
        )
    }

  private def checkPbfFileDefinition(pbf: OsmoGridConfig.Input.Osm.Pbf): Unit =
    if (pbf.file.isEmpty) throw IllegalConfigException("Pbf file may be set!")

  private def checkOutputConfig(
      output: OsmoGridConfig.Output,
      additionalListener: Seq[ActorRef[ResultListener.ResultEvent]]
  ): Unit =
    output match {
      case Output(Some(file)) =>
        checkOutputFile(file)
      case Output(None) if additionalListener.nonEmpty =>
        logger.info(
          "No output data type defined, but other listener provided. Will use them accordingly!"
        )
      case Output(None) =>
        throw IllegalConfigException(
          "You have to provide at least one output data sink, e.g. to .csv-files!"
        )
    }

  private def checkOutputFile(file: OsmoGridConfig.Output.Csv): Unit = if (
    file.directory.isEmpty || file.separator.isEmpty
  )
    throw IllegalConfigException(
      "Output directory and separator must be set when using .csv file sink!"
    )
}
