/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.cfg

import akka.actor.typed.ActorRef
import akka.routing.ActorRefRoutee
import com.typesafe.scalalogging.LazyLogging
import edu.ie3.osmogrid.cfg.OsmoGridConfig.{Input, Output}
import edu.ie3.osmogrid.cfg.OsmoGridConfig.Input.{Asset, Osm}
import edu.ie3.osmogrid.cfg.OsmoGridConfig.Generation
import edu.ie3.osmogrid.cfg.OsmoGridConfig.Generation.Lv
import edu.ie3.osmogrid.cfg.OsmoGridConfig.Input.Asset.File
import edu.ie3.osmogrid.exception.IllegalConfigException
import edu.ie3.osmogrid.io.output.ResultListener.ResultEvent

object ConfigFailFast extends LazyLogging {
  def check(
      cfg: OsmoGridConfig,
      additionalListener: Vector[ActorRef[ResultEvent]] = Vector.empty
  ): Unit = cfg match {
    case OsmoGridConfig(generation, input, output) =>
      checkInputConfig(input)
      checkOutputConfig(output, additionalListener)
      checkGenerationConfig(generation)
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
          amountOfGridGenerators,
          amountOfRegionCoordinators,
          distinctHouseConnections
        ) =>
      if (amountOfGridGenerators < 1)
        throw IllegalConfigException(
          s"The amount of lv grid generation actors needs to be at least 1 (provided: $amountOfGridGenerators)."
        )
      if (amountOfRegionCoordinators < 1)
        throw IllegalConfigException(
          s"The amount of lv region coordination actors needs to be at least 1 (provided: $amountOfRegionCoordinators)."
        )
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
    file match {
      case File(directory, _) if directory.isEmpty =>
        throw IllegalConfigException("Asset input directory may be set!")
      case _ => /* I don't care. Everything is fine. */
    }

  private def checkOsmInputConfig(osm: OsmoGridConfig.Input.Osm): Unit =
    osm match {
      case Osm(Some(file)) => checkPbfFileDefinition(file)
      case Osm(None) =>
        throw IllegalConfigException(
          "You have to provide at least one input data type for open street map information!"
        )
    }

  private def checkPbfFileDefinition(pbf: OsmoGridConfig.Input.Osm.Pbf): Unit =
    pbf match {
      case OsmoGridConfig.Input.Osm.Pbf(file) if file.isEmpty =>
        throw IllegalConfigException("Pbf file may be set!")
      case _ => /* I don't care. Everything is fine. */
    }

  private def checkOutputConfig(
      output: OsmoGridConfig.Output,
      additionalListener: Vector[ActorRef[ResultEvent]]
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
          "You have to provide at least one output data type!"
        )
    }

  private def checkOutputFile(file: OsmoGridConfig.Output.File): Unit =
    file match {
      case OsmoGridConfig.Output.File(directory, _) if directory.isEmpty =>
        throw IllegalConfigException("Output directory may be set!")
      case _ => /* I don't care. Everything is fine. */
    }
}
