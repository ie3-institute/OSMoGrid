/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.cfg

import edu.ie3.osmogrid.cfg.OsmoGridConfig.{Input, Output}
import edu.ie3.osmogrid.cfg.OsmoGridConfig.Input.{Asset, Osm}
import edu.ie3.osmogrid.cfg.OsmoGridConfig.Generation
import edu.ie3.osmogrid.cfg.OsmoGridConfig.Generation.Lv
import edu.ie3.osmogrid.exception.IllegalConfigException

object ConfigFailFast {
  def check(cfg: OsmoGridConfig): Unit = cfg match {
    case OsmoGridConfig(generation, input, output) =>
      checkInputConfig(input)
      checkOutputConfig(output)
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
    case Lv(distinctHouseConnections) => /* I don't care. Everything is fine. */
  }

  private def checkInputConfig(input: OsmoGridConfig.Input): Unit =
    input match {
      case Input(asset, osm) =>
        checkAssetInputConfig(asset)
        checkOsmInputConfig(osm)
    }

  private def checkAssetInputConfig(asset: OsmoGridConfig.Input.Asset): Unit =
    asset match {
      case Asset(Some(file)) => /* I don't care. Everything is fine. */
      case Asset(None) =>
        throw IllegalConfigException(
          "You have to provide at least one input data type for asset information!"
        )
    }

  private def checkOsmInputConfig(osm: OsmoGridConfig.Input.Osm): Unit =
    osm match {
      case Osm(Some(file)) => /* I don't care. Everything is fine. */
      case Osm(None) =>
        throw IllegalConfigException(
          "You have to provide at least one input data type for open street map information!"
        )
    }

  private def checkOutputConfig(output: OsmoGridConfig.Output): Unit =
    output match {
      case Output(Some(file)) => /* I don't care. Everything is fine. */
      case Output(None) =>
        throw IllegalConfigException(
          "You have to provide at least one output data type!"
        )
    }
}
