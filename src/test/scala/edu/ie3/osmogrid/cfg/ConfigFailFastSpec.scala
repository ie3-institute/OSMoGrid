/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.cfg

import edu.ie3.test.common.UnitSpec

import scala.util.{Failure, Success}

class ConfigFailFastSpec extends UnitSpec {
  "Checking a given config for validity" when {
    "having malicious input definition" should {
      "fail on missing osm input definition" in {
        OsmoGridConfigFactory.parseWithoutFallback {
          """input.asset.file.directory = "asset_input_dir"
            |input.asset.file.separator = ","
            |input.asset.file.hierarchic = false
            |output.csv.directory = "output_file_path"
            |output.gridName = "test_grid"
            |""".stripMargin
        } match {
          case Success(cfg) =>
            ConfigFailFast.check(cfg) match {
              case Failure(exception) =>
                exception.getMessage shouldBe "You have to provide at least one input data type for open street map information!"
              case Success(_) =>
                fail("Config check succeeded, but was meant to fail.")
            }
          case Failure(exception) =>
            fail(s"Config generation failed with an exception: '$exception'")
        }
      }

      "fail on empty osm input definition" in {
        OsmoGridConfigFactory.parseWithoutFallback {
          """input.osm.pbf.file = ""
            |input.asset.file.directory = "asset_input_dir"
            |input.asset.file.separator = ","
            |input.asset.file.hierarchic = false
            |output.csv.directory = "output_file_path"
            |output.gridName = "test_grid"
            |""".stripMargin
        } match {
          case Success(cfg) =>
            ConfigFailFast.check(cfg) match {
              case Failure(exception) =>
                exception.getMessage shouldBe "Pbf file may be set!"
              case Success(_) =>
                fail("Config check succeeded, but was meant to fail.")
            }
          case Failure(exception) =>
            fail(s"Config generation failed with an exception: '$exception'")
        }
      }

      "fail on missing asset input definition" in {
        OsmoGridConfigFactory.parseWithoutFallback {
          """input.osm.pbf.file = "pbf_file"
            |output.csv.directory = "output_file_path"
            |output.gridName = "test_grid"
            |""".stripMargin
        } match {
          case Success(cfg) =>
            ConfigFailFast.check(cfg) match {
              case Failure(exception) =>
                exception.getMessage shouldBe "You have to provide at least one input data type for asset information!"
              case Success(_) =>
                fail("Config check succeeded, but was meant to fail.")
            }
          case Failure(exception) =>
            fail(s"Config generation failed with an exception: '$exception'")
        }
      }

      "fail on empty asset directory definition" in {
        OsmoGridConfigFactory.parseWithoutFallback {
          """input.osm.pbf.file = ""
            |input.asset.file.directory = ""
            |input.asset.file.separator = ","
            |input.asset.file.hierarchic = false
            |output.csv.directory = "output_file_path"
            |output.gridName = "test_grid"
            |""".stripMargin
        } match {
          case Success(cfg) =>
            ConfigFailFast.check(cfg) match {
              case Failure(exception) =>
                exception.getMessage shouldBe "Asset input directory may be set!"
              case Success(_) =>
                fail("Config check succeeded, but was meant to fail.")
            }
          case Failure(exception) =>
            fail(s"Config generation failed with an exception: '$exception'")
        }
      }

      "fail on invalid lowest admin level" in {
        OsmoGridConfigFactory.parseWithoutFallback {
          """input.osm.pbf.file = "pbf_file"
            |input.asset.file.directory = "asset_input_dir"
            |input.asset.file.hierarchic = false
            |output.csv.directory = "output_file_path"
            |output.gridName = "test_grid"
            |generation.lv.averagePowerDensity = 12.5
            |generation.lv.ratedVoltage = 0.4
            |generation.lv.considerHouseConnectionPoints = false
            |generation.lv.boundaryAdminLevel.starting = 2
            |generation.lv.boundaryAdminLevel.lowest = 99
            |""".stripMargin.stripMargin
        } match {
          case Success(cfg) =>
            ConfigFailFast.check(cfg) match {
              case Failure(exception) =>
                exception.getMessage shouldBe "The lowest admin level can not be parsed (provided: 99)."
              case Success(_) =>
                fail("Config check succeeded, but was meant to fail.")
            }
          case Failure(exception) =>
            fail(s"Config generation failed with an exception: '$exception'")
        }
      }

      "fail on invalid starting admin level" in {
        OsmoGridConfigFactory.parseWithoutFallback {
          """input.osm.pbf.file = "pbf_file"
            |input.asset.file.directory = "asset_input_dir"
            |input.asset.file.separator = ","
            |input.asset.file.hierarchic = false
            |output.csv.directory = "output_file_path"
            |output.gridName = "test_grid"
            |generation.lv.averagePowerDensity = 12.5
            |generation.lv.ratedVoltage = 0.4
            |generation.lv.considerHouseConnectionPoints = false
            |generation.lv.boundaryAdminLevel.starting = -1
            |generation.lv.boundaryAdminLevel.lowest = 8
            |""".stripMargin
        } match {
          case Success(cfg) =>
            ConfigFailFast.check(cfg) match {
              case Failure(exception) =>
                exception.getMessage shouldBe "The starting admin level can not be parsed (provided: -1)."
              case Success(_) =>
                fail("Config check succeeded, but was meant to fail.")
            }
          case Failure(exception) =>
            fail(s"Config generation failed with an exception: '$exception'")
        }
      }

      "fail on starting level bigger than lowest level" in {
        OsmoGridConfigFactory.parseWithoutFallback {
          """input.osm.pbf.file = "pbf_file"
            |input.asset.file.directory = "asset_input_dir"
            |input.asset.file.separator = ","
            |input.asset.file.hierarchic = false
            |output.csv.directory = "output_file_path"
            |output.gridName = "test_grid"
            |generation.lv.averagePowerDensity = 12.5
            |generation.lv.ratedVoltage = 0.4
            |generation.lv.considerHouseConnectionPoints = false
            |generation.lv.boundaryAdminLevel.starting = 5
            |generation.lv.boundaryAdminLevel.lowest = 4
            |""".stripMargin
        } match {
          case Success(cfg) =>
            ConfigFailFast.check(cfg) match {
              case Failure(exception) =>
                exception.getMessage shouldBe "The starting admin level (provided: STATE_DISTRICT_LEVEL) " +
                  "has to be higher than the lowest admin level (provided: FEDERAL_STATE_LEVEL)."
              case Success(_) =>
                fail("Config check succeeded, but was meant to fail.")
            }
          case Failure(exception) =>
            fail(s"Config generation failed with an exception: '$exception'")
        }
      }
    }

    "having missing generation configs" should {
      "fail" in {
        OsmoGridConfigFactory.parseWithoutFallback {
          """input.osm.pbf.file = "pbf_file"
            |input.asset.file.directory = "asset_input_dir"
            |input.asset.file.separator = ","
            |input.asset.file.hierarchic = false
            |output.csv.directory = "output_file_path"
            |output.gridName = "test_grid"
            |""".stripMargin
        } match {
          case Success(cfg) =>
            ConfigFailFast.check(cfg) match {
              case Failure(exception) =>
                exception.getMessage shouldBe "At least one voltage level generation config has to be defined."
              case Success(_) =>
                fail("Config check succeeded, but was meant to fail.")
            }
          case Failure(exception) =>
            fail(s"Config generation failed with an exception: '$exception'")
        }
      }
    }

    "having a valid config" should {
      "pass" in {
        OsmoGridConfigFactory.parseWithoutFallback {
          """input.osm.pbf.file = "pbf_file"
            |input.asset.file.directory = "asset_input_dir"
            |input.asset.file.separator = ","
            |input.asset.file.hierarchic = false
            |output.csv.directory = "output_file_path"
            |output.gridName = "test_grid"
            |generation.lv.averagePowerDensity = 12.5
            |generation.lv.ratedVoltage = 0.4
            |generation.lv.considerHouseConnectionPoints = false
            |generation.lv.boundaryAdminLevel.starting = 2
            |generation.lv.boundaryAdminLevel.lowest = 8
            |""".stripMargin
        } match {
          case Success(cfg) => ConfigFailFast.check(cfg) shouldBe Success(cfg)
          case Failure(exception) =>
            fail(s"Config generation failed with an exception: '$exception'")
        }
      }
    }
  }
}
