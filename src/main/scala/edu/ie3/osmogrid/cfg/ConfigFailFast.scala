/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.cfg

import org.apache.pekko.actor.typed.ActorRef
import com.typesafe.scalalogging.LazyLogging
import edu.ie3.osmogrid.cfg.OsmoGridConfig.Generation.{Lv, Mv}
import edu.ie3.osmogrid.cfg.OsmoGridConfig.Input.{Asset, Osm}
import edu.ie3.osmogrid.cfg.OsmoGridConfig.{
  Generation,
  Grids,
  Input,
  Output,
  Voltage
}
import edu.ie3.osmogrid.exception.IllegalConfigException
import edu.ie3.osmogrid.io.input.BoundaryAdminLevel
import edu.ie3.osmogrid.io.output.OutputRequest

import scala.util.Try

object ConfigFailFast extends LazyLogging {
  def check(
      cfg: OsmoGridConfig,
      additionalListener: Seq[ActorRef[OutputRequest]] = Seq.empty
  ): Try[OsmoGridConfig] = Try {
    cfg match {
      case OsmoGridConfig(generation, grids, input, output, voltage) =>
        checkInputConfig(input)
        checkOutputConfig(output, additionalListener)
        checkGenerationConfig(generation)
        checkGridsConfig(grids)
        checkVoltageConfig(voltage)
    }
    cfg
  }

  private def checkGenerationConfig(generation: Generation): Unit =
    generation match {
      case Generation(lv, mv) =>
        /* Check, that at least one config is set */
        val defined = Seq(lv, mv).count(_.isDefined)

        if (defined < 1)
          throw IllegalConfigException(
            "At least one generation config has to be defined."
          )

        /* Check single configs */
        lv.foreach(checkLvConfig)
        mv.foreach(checkMvConfig)
    }

  // TODO Check Filter for osm
  private def checkLvConfig(lv: OsmoGridConfig.Generation.Lv): Unit = lv match {
    case Lv(
          _,
          Lv.BoundaryAdminLevel(
            lowest,
            starting
          ),
          _,
          loadSimultaneousFactor,
          _,
          _
        ) =>
      (BoundaryAdminLevel.get(lowest), BoundaryAdminLevel.get(starting)) match {
        case (None, _) =>
          throw IllegalConfigException(
            s"The lowest admin level can not be parsed (provided: $lowest)."
          )
        case (_, None) =>
          throw IllegalConfigException(
            s"The starting admin level can not be parsed (provided: $starting)."
          )
        case (Some(lowestParsed), Some(startingParsed))
            if startingParsed.osmLevel > lowestParsed.osmLevel =>
          throw IllegalConfigException(
            s"The starting admin level (provided: $startingParsed) has to be higher than the lowest admin level (provided: $lowestParsed)."
          )
        case _ =>
        // all good, do nothing
      }

      if (loadSimultaneousFactor < 0 || loadSimultaneousFactor > 1) {
        throw IllegalConfigException(
          s"The load simultaneous factor (provided: $loadSimultaneousFactor) has to be between 0 and 1."
        )
      }
  }

  // TODO: Add some checks if necessary in the future
  private def checkMvConfig(mv: OsmoGridConfig.Generation.Mv): Unit = mv match {
    case Mv(spawnMissingHvNodes) =>
      spawnMissingHvNodes match {
        case true  =>
        case false =>
        case other =>
          throw IllegalConfigException(
            s"The given value for `spawnMissingHvNodes` can not be parsed (provided: $other)."
          )
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
      additionalListener: Seq[ActorRef[OutputRequest]]
  ): Unit =
    output match {
      case Output(Some(file), _) =>
        checkOutputFile(file)
      case Output(None, _) if additionalListener.nonEmpty =>
        logger.info(
          "No output data type defined, but other listener provided. Will use them accordingly!"
        )
      case Output(None, _) =>
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

  private def checkGridsConfig(grids: Grids): Unit = {
    grids.output match {
      case Grids.Output(false, false, false) =>
        // at least one output must be set
        throw IllegalConfigException(s"No grid output defined.")

      case Grids.Output(true, false, false) =>
        logger.warn(s"Only hv output is currently not supported!")
      case _ =>

    }
  }

  // TODO: Check if necessary
  private def checkVoltageConfig(voltage: Voltage): Unit = {}
}
