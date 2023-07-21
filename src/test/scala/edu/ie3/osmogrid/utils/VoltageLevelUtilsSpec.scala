/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.utils

import com.typesafe.config.ConfigFactory
import edu.ie3.datamodel.models.voltagelevels.VoltageLevel
import edu.ie3.osmogrid.cfg.{OsmoGridConfig, OsmoGridConfigFactory}
import edu.ie3.test.common.UnitSpec
import tech.units.indriya.quantity.Quantities
import tech.units.indriya.unit.Units
import utils.VoltageLevelUtils

import java.nio.file.Paths

class VoltageLevelUtilsSpec extends UnitSpec {

  "The VoltageLevelUtils" should {
    val cfgWithVoltLvl: OsmoGridConfig.Generation = OsmoGridConfig(
      ConfigFactory.parseFile(
        Paths
          .get(
            "src",
            "test",
            "resources",
            "edu",
            "ie3",
            "osmogrid",
            "utils",
            "definedVoltageLevels.conf"
          )
          .toFile
      )
    ).generation

    val cfgWithoutVoltLvl: OsmoGridConfig.Generation = OsmoGridConfig(
      ConfigFactory.parseFile(
        Paths
          .get(
            "src",
            "test",
            "resources",
            "edu",
            "ie3",
            "osmogrid",
            "utils",
            "voltageLevel.conf"
          )
          .toFile
      )
    ).generation

    "return a list of all lv voltage levels" in {
      val voltLvl = cfgWithVoltLvl.lv match {
        case Some(value) => value.voltageLevel
      }

      val levels: List[VoltageLevel] =
        VoltageLevelUtils.getVoltLvl("lv", voltLvl)

      levels.size shouldBe 1
      val level = levels(0)

      level.getId shouldBe "lv"
      level.getNominalVoltage shouldBe Quantities.getQuantity(400, Units.VOLT)
    }
  }
}
